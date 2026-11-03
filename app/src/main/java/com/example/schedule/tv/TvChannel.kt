package com.example.schedule.tv

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.example.schedule.R
import com.example.schedule.data.RaceRepository
import com.example.schedule.data.TimedSession
import com.example.schedule.data.WeekendSnapshot
import com.example.schedule.data.WeekendTimeline
import com.example.schedule.model.Race
import com.example.schedule.tv.TvSupport.State
import com.example.schedule.ui.Format
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * "MotoGP Weekend" row on the TV home screen: one 16:9 card per session of the current
 * round, in local time. Uses the standard Android TV preview channel (TvProvider), so any
 * launcher that shows app channels can display it: Android TV home, Monet (custom media
 * row → GP Schedule), Projectivy and others.
 *
 * Cards are ordered: live / next first, then the rest of the weekend, finished ones last.
 * They're rewritten whenever the widgets refresh (session start / end, midnight, import,
 * boot, time-zone change). No countdowns on the cards, so they're never stale in between.
 */
object TvChannel {

    private const val TAG = "TvChannel"
    private const val PREFS = "tv_channel"
    private const val KEY_CHANNEL_ID = "channel_id"
    private const val CHANNEL_INTERNAL_ID = "motogp_weekend"
    private const val COL_INTERNAL_ID = "internal_provider_id"
    private const val ART_VERSION = "v1"

    const val EXTRA_FOCUS_START = "tv_focus_start"

    private val executor = Executors.newSingleThreadExecutor()

    /** True on a TV that can show app channels (Android 8+). */
    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && TvSupport.isTv(context)

    /**
     * Rebuilds the row in the background. Returns true when this device keeps the row
     * (so the exact refresh alarm should stay on), false on phones.
     */
    fun refreshAsync(context: Context, done: (() -> Unit)? = null): Boolean {
        val app = context.applicationContext
        if (!isSupported(app)) {
            done?.invoke()
            return false
        }
        executor.execute {
            try {
                refresh(app)
            } catch (e: Exception) {
                Log.e(TAG, "Channel refresh failed", e)
            } finally {
                done?.invoke()
            }
        }
        return true
    }

    // ------------------------------------------------------------------

    private data class Card(
        val key: String,
        val session: TimedSession,
        val state: State,
        val art: TvCardArt.Content
    )

    private fun refresh(context: Context) {
        val channelId = ensureChannel(context) ?: return

        val now = System.currentTimeMillis()
        val races = RaceRepository.getRaces(context)
        val snap = WeekendTimeline.snapshot(races, now)

        val cards = if (snap == null) emptyList() else cards(context, snap, now)
        val files = cards.associate { it.key to artFile(context, it.art) }

        val resolver = context.contentResolver
        val existing = existingPrograms(context, channelId)

        // Same cards in the same order → update in place (no flicker). Otherwise rebuild
        // so launchers that ignore the weight still show the right order.
        val sameOrder = existing.map { it.second } == cards.map { it.key }

        if (!sameOrder) {
            existing.forEach { (id, _) ->
                resolver.delete(TvContractCompat.buildPreviewProgramUri(id), null, null)
            }
        }

        cards.forEachIndexed { index, card ->
            val values = program(context, channelId, snap!!.race, card, files.getValue(card.key), weight = (cards.size - index) * 10)
                .toContentValues()
            if (sameOrder) {
                resolver.update(TvContractCompat.buildPreviewProgramUri(existing[index].first), values, null, null)
            } else {
                resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, values)
            }
        }
        // Old images only go once no card points at them any more
        cleanArt(context, files.values.map { it.name }.toSet())
        Log.d(TAG, "Channel $channelId: ${cards.size} cards (${if (sameOrder) "updated" else "rebuilt"})")
    }

    /** Live / next / to come in order, then finished sessions */
    private fun cards(context: Context, snap: WeekendSnapshot, now: Long): List<Card> {
        val all = snap.sessions.map { s ->
            val state = TvSupport.state(s, snap, now)
            Card(
                key = TvSupport.sessionKey(snap.race, s),
                session = s,
                state = state,
                art = TvCardArt.content(context, snap.race, s, state, now)
            )
        }
        return all.filter { it.state != State.DONE } + all.filter { it.state == State.DONE }
    }

    private fun program(
        context: Context,
        channelId: Long,
        race: Race,
        card: Card,
        art: File,
        weight: Int
    ): PreviewProgram {
        val s = card.session
        val status = when (card.state) {
            State.LIVE -> "Live now · ${Format.left(System.currentTimeMillis(), s.end)}"
            State.NEXT -> "Next session"
            State.DONE -> "Finished"
            State.UPCOMING -> Format.dayLabel(s.start)
        }
        val open = Intent(context, TvScheduleActivity::class.java)
            .putExtra(EXTRA_FOCUS_START, s.start)

        return PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(TvContractCompat.PreviewPrograms.TYPE_EVENT)
            .setTitle("${TvSupport.label(s)} · ${card.art.dayTime.replace("  ", " ")}")
            .setDescription("${TvSupport.roundText(race)} ${Format.city(race)} · ${s.longLabel} · $status")
            .setPosterArtUri(TvArtProvider.uri(context, art.name))
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
            .setIntentUri(Uri.parse(open.toUri(Intent.URI_INTENT_SCHEME)))
            .setInternalProviderId(card.key)
            .setLive(card.state == State.LIVE)
            .setStartTimeUtcMillis(s.start)
            .setEndTimeUtcMillis(s.end)
            .setWeight(weight)
            .build()
    }

    // ------------------------------------------------------------------
    // CHANNEL
    // ------------------------------------------------------------------

    /** Our channel's id, creating it (and asking for it on the home screen) the first time. */
    private fun ensureChannel(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getLong(KEY_CHANNEL_ID, -1L)
        if (saved != -1L && channelExists(context, saved)) return saved

        val found = findChannel(context)
        if (found != null) {
            prefs.edit().putLong(KEY_CHANNEL_ID, found).apply()
            return found
        }

        val appLink = Intent(context, TvScheduleActivity::class.java)
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(context.getString(R.string.tv_channel_name))
            .setDescription(context.getString(R.string.tv_channel_description))
            .setAppLinkIntentUri(Uri.parse(appLink.toUri(Intent.URI_INTENT_SCHEME)))
            .setInternalProviderId(CHANNEL_INTERNAL_ID)
            .build()

        val uri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
            ?: run {
                Log.w(TAG, "TvProvider refused the channel")
                return null
            }
        val id = ContentUris.parseId(uri)
        prefs.edit().putLong(KEY_CHANNEL_ID, id).apply()

        try {
            ChannelLogoUtils.storeChannelLogo(context, id, TvCardArt.logo(context))
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't store the channel logo", e)
        }
        try {
            // First channel of an app: shown on the home screen without a prompt on Android TV
            TvContractCompat.requestChannelBrowsable(context, id)
        } catch (e: Exception) {
            Log.w(TAG, "requestChannelBrowsable failed", e)
        }
        return id
    }

    private fun channelExists(context: Context, id: Long): Boolean = try {
        context.contentResolver.query(
            TvContractCompat.buildChannelUri(id), arrayOf(BaseColumns._ID), null, null, null
        )?.use { it.moveToFirst() } ?: false
    } catch (e: Exception) {
        false
    }

    /** Only our own channels are visible to us */
    private fun findChannel(context: Context): Long? = try {
        context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            arrayOf(BaseColumns._ID, COL_INTERNAL_ID),
            null, null, null
        )?.use { c ->
            var match: Long? = null
            while (c.moveToNext()) {
                if (c.getString(1) == CHANNEL_INTERNAL_ID) {
                    match = c.getLong(0)
                    break
                }
            }
            match
        }
    } catch (e: Exception) {
        null
    }

    /** (program id, session key), in insertion order */
    private fun existingPrograms(context: Context, channelId: Long): List<Pair<Long, String>> = try {
        context.contentResolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            arrayOf(BaseColumns._ID, COL_INTERNAL_ID),
            null, null, BaseColumns._ID
        )?.use { c ->
            val list = ArrayList<Pair<Long, String>>()
            while (c.moveToNext()) list.add(c.getLong(0) to (c.getString(1) ?: ""))
            list
        } ?: emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read existing cards", e)
        emptyList()
    }

    // ------------------------------------------------------------------
    // ART
    // ------------------------------------------------------------------

    /** Renders the card only when its content changed; the name changes with it, so launchers reload. */
    private fun artFile(context: Context, content: TvCardArt.Content): File {
        val file = File(TvArtProvider.dir(context), "c_${hash("$ART_VERSION|$content")}.png")
        if (!file.exists()) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            val bmp = TvCardArt.render(context, content)
            tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            if (!tmp.renameTo(file)) tmp.delete()
        }
        return file
    }

    private fun cleanArt(context: Context, keep: Set<String>) {
        TvArtProvider.dir(context).listFiles()?.forEach { f ->
            if (f.name !in keep) f.delete()
        }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .take(10).joinToString("") { "%02x".format(it) }
}
