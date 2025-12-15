package com.example.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.schedule.model.Race
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RaceFragment : Fragment() {

    companion object {
        private const val ARG_IS_MOTOGP = "is_motogp"

        fun newInstance(isMotoGP: Boolean): RaceFragment {
            val fragment = RaceFragment()
            val args = Bundle()
            args.putBoolean(ARG_IS_MOTOGP, isMotoGP)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_race, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)

        val isMotoGP = arguments?.getBoolean(ARG_IS_MOTOGP, true) ?: true
        val jsonFile = if (isMotoGP) "motogp_races.json" else "sbk_races.json"

        val jsonString = requireContext().assets.open(jsonFile).bufferedReader().use { it.readText() }
        val gson = Gson()
        val mapType = object : TypeToken<Map<String, List<Race>>>() {}.type
        val jsonMap: Map<String, List<Race>> = gson.fromJson(jsonString, mapType)
        val races = jsonMap["races"] ?: emptyList()

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = RaceAdapter(races)

        return view
    }
}