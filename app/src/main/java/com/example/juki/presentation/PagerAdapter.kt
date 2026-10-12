package com.example.juki.presentation

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.nmichail.android_pmu.presentation.ui.AuthorsFragment

class PagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RegistrationFragment()
            1 -> RulesFragment()
            2 -> AuthorsFragment()
            3 -> SettingsFragment()
            4 -> RecordsFragment()
            else -> error("Неизвестная вкладка: $position")
        }
    }
}