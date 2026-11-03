package com.example.schedule

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.schedule.data.RaceRepository

class RaceFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var adapter: RaceAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0

    // Reload the list whenever a new schedule is imported or reset
    private val scheduleListener: () -> Unit = { loadRaces() }
    private val weatherListener: () -> Unit = { adapter?.weatherChanged() }

    /** Countdown every second; re-check which round is featured every 30 s */
    private val ticker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            adapter?.let {
                if (++ticks % 30 == 0) it.refresh(now)
                it.tick(now)
            }
            handler.postDelayed(this, 1000L - now % 1000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_race, container, false)
        recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = null
        }
        loadRaces()
        RaceRepository.addListener(scheduleListener)
        com.example.schedule.weather.Weather.addListener(weatherListener)
        return view
    }

    override fun onResume() {
        super.onResume()
        adapter?.refresh(System.currentTimeMillis())
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroyView() {
        RaceRepository.removeListener(scheduleListener)
        com.example.schedule.weather.Weather.removeListener(weatherListener)
        handler.removeCallbacks(ticker)
        recyclerView = null
        adapter = null
        super.onDestroyView()
    }

    private fun loadRaces() {
        val list = recyclerView ?: return
        val context = context ?: return
        adapter = RaceAdapter(context, RaceRepository.getRaces(context)).also { list.adapter = it }
    }
}
