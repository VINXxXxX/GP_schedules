package com.example.schedule

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        viewPager.adapter = RacePagerAdapter(this)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_motogp -> viewPager.currentItem = 0
                R.id.nav_sbk -> viewPager.currentItem = 1
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.selectedItemId = if (position == 0) R.id.nav_motogp else R.id.nav_sbk
            }
        })
    }
}