#!/usr/bin/env python3
"""
Updates schedule/motogp_<year>.json from MotoGP's own timetable feed.

  python tools/sync_schedule.py schedule

Same rules as the app (ScheduleSync.kt):
  * times are stored in IST ("01:30 PM") with dayOffset from the round's Friday, like the app's JSON
  * only sessions that fall inside the round's weekend (dayOffset 0..6) are taken
  * a session already in the file moves at most 3 h per run, unless its time is TBA or a guess
    (SQ / sighting laps) — bigger jumps are reported, not applied
  * sessions the feed has and the file doesn't (e.g. sighting laps) are added
Standard library only.
"""
import datetime as dt
import json
import os
import re
import sys
import urllib.request

API = "https://api.motogp.pulselive.com/motogp/v1"
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
MAX_SHIFT = dt.timedelta(hours=3)
PREDICTED = {"sq", "sighting"}
TIME_FMT = "%I:%M %p"

# What the app calls each session (SessionCatalog.canonical)
ALIASES = {
    "sprint_quali": "sq", "sprint qualifying": "sq", "sprintq": "sq",
    "spr": "sprint", "gp": "race", "grand prix": "race", "wup": "warmup", "warm up": "warmup",
}


def canonical(name):
    n = name.strip().lower()
    return ALIASES.get(n, n)


def from_motogp(short, name, season):
    """Feed name → our session name. 2026 'PR' is our fp2; from 2027 'FP2' is fp2."""
    s = (short or "").strip().upper()
    n = (name or "").strip().upper()
    new = season >= 2027
    if s == "FP1": return "fp1"
    if s == "PR": return "fp2"
    if s == "FP2": return "fp2" if new else "fp3"
    if s == "FP3": return "fp3"
    if s == "SQ" or "SPRINT QUAL" in n: return "sq"
    if s == "Q1": return "q1"
    if s == "Q2": return "q2"
    if s in ("SPR", "SPRINT") or ("SPRINT" in n and "QUAL" not in n): return "sprint"
    if s == "WUP" or "WARM" in n: return "warmup"
    if s.startswith("SIGHT") or "SIGHTING" in n: return "sighting"
    if s in ("RAC", "RACE") or n == "RACE" or "GRAND PRIX" in n: return "race"
    if not new and n == "PRACTICE": return "fp2"
    return None


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "GPSchedule-sync/1", "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode("utf-8"))


def fetch_events(season):
    last = None
    for url in (f"{API}/events?seasonYear={season}", f"{API}/events/seasonYear={season}"):
        try:
            data = get_json(url)
            if isinstance(data, list):
                return data
        except Exception as e:  # try the other form
            last = e
    raise RuntimeError(f"MotoGP feed unavailable: {last}")


def parse_time(raw):
    if not raw:
        return None
    v = raw.strip().replace("Z", "+00:00")
    v = re.sub(r"([+-]\d{2})(\d{2})$", r"\1:\2", v)  # +0100 → +01:00
    try:
        return dt.datetime.fromisoformat(v)
    except ValueError:
        return None


def friday(race):
    y, m, d = (int(x) for x in race["race"].split("-"))
    return dt.datetime(y, m, d, tzinfo=IST)


def session_start(race, s):
    if s["sessionTime"].upper() in ("TBA", "TBC", "TBD"):
        return None
    t = dt.datetime.strptime(s["sessionTime"].strip().upper(), TIME_FMT)
    day = friday(race) + dt.timedelta(days=int(s.get("dayOffset", 0)))
    return day.replace(hour=t.hour, minute=t.minute)


def to_ist(race, when):
    local = when.astimezone(IST)
    offset = (local.date() - friday(race).date()).days
    if not 0 <= offset <= 6:
        return None
    return local.strftime(TIME_FMT), offset


def weekend_end(race):
    starts = [session_start(race, s) for s in race["sessions"]]
    starts = [x for x in starts if x]
    return max(starts) + dt.timedelta(hours=2) if starts else friday(race) + dt.timedelta(days=3)


def sync_file(path, report):
    season = int(re.search(r"(\d{4})", os.path.basename(path)).group(1))
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    races = doc["races"] if isinstance(doc, dict) else doc

    now = dt.datetime.now(dt.timezone.utc)
    if all(weekend_end(r) < now - dt.timedelta(days=2) for r in races):
        report.append(f"{season}: season over, skipped")
        return False

    events = fetch_events(season)
    changed = False
    for ev in events:
        if str(ev.get("kind", "")).upper() == "TEST":
            continue
        proposals = {}
        for b in ev.get("broadcasts") or []:
            cat = b.get("category") or {}
            is_mgp = str(cat.get("acronym", "")).upper() == "MGP" or str(cat.get("name", "")).upper().startswith("MOTOGP")
            if not is_mgp or str(b.get("type", "SESSION")).upper() != "SESSION":
                continue
            name = from_motogp(b.get("shortname"), b.get("name"), season)
            when = parse_time(b.get("date_start"))
            if name and when:
                proposals[name] = when
        if not proposals:
            continue

        first = min(proposals.values())
        race = min(races, key=lambda r: abs((friday(r) - first).total_seconds()))
        if abs((friday(race) - first).total_seconds()) > 3 * 86400:
            continue

        for name, when in sorted(proposals.items(), key=lambda kv: kv[1]):
            target = to_ist(race, when)
            if not target:
                continue
            time, offset = target
            mine = next((s for s in race["sessions"] if canonical(s["sessionName"]) == name), None)
            if mine is None:
                race["sessions"].append({"sessionName": name, "sessionTime": time, "dayOffset": offset})
                report.append(f"R{race['round']} {name}: added {time} (day {offset})")
                changed = True
                continue
            if mine["sessionTime"] == time and int(mine.get("dayOffset", 0)) == offset:
                continue
            old = session_start(race, mine)
            if old is not None and name not in PREDICTED and abs(when - old) > MAX_SHIFT:
                report.append(f"⚠ R{race['round']} {name}: {mine['sessionTime']} → {time} looks too big, NOT applied")
                continue
            report.append(f"R{race['round']} {name}: {mine['sessionTime']} (day {mine.get('dayOffset', 0)}) → {time} (day {offset})")
            mine["sessionTime"], mine["dayOffset"] = time, offset
            changed = True

        race["sessions"].sort(key=lambda s: (int(s.get("dayOffset", 0)),
                                             session_start(race, s) or friday(race) + dt.timedelta(days=9)))

    if changed:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False, indent=2)
            f.write("\n")
    return changed


def main():
    folder = sys.argv[1] if len(sys.argv) > 1 else "schedule"
    report, failed = [], False
    for name in sorted(os.listdir(folder)):
        if not re.fullmatch(r"motogp_\d{4}\.json", name):
            continue
        try:
            sync_file(os.path.join(folder, name), report)
        except Exception as e:
            failed = True
            report.append(f"{name}: {e}")
    text = "\n".join(report) or "No changes."
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("### MotoGP schedule sync\n\n```\n" + text + "\n```\n")
    # A feed outage is not a failure of the job: the files simply stay as they are
    return 0 if not failed or report else 1


if __name__ == "__main__":
    sys.exit(main())
