package com.example.schedule

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class RacePagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RaceFragment()       // MotoGP
            1 -> SettingsFragment()   // Settings
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
}
