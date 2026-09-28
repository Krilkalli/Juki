package com.example.juki.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.juki.R

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<SeekBar>(R.id.seekGameSpeed)
            .bindTo(view.findViewById(R.id.tvGameSpeed), R.string.settings_game_speed)
        view.findViewById<SeekBar>(R.id.seekMaxTarakani)
            .bindTo(view.findViewById(R.id.tvMaxTarakani), R.string.settings_max_tarakani)
        view.findViewById<SeekBar>(R.id.seekBonusInterval)
            .bindTo(view.findViewById(R.id.tvBonusInterval), R.string.settings_bonus_interval)
        view.findViewById<SeekBar>(R.id.seekRoundDuration)
            .bindTo(view.findViewById(R.id.tvRoundDuration), R.string.settings_round_duration)
    }

    private fun SeekBar.bindTo(label: TextView, labelRes: Int) {
        fun update() {
            label.text = context.getString(labelRes, progress)
        }

        update()

        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                update()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }
}