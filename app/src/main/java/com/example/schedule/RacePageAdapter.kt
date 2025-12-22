package com.example.schedule

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class RacePagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RaceFragment.newInstance(true)   // MotoGP
            1 -> RaceFragment.newInstance(false)  // SBK
            2 -> SettingsFragment()               // SETTINGS ✅
            else -> throw IllegalStateException("Invalid position $position")
        }    }
}