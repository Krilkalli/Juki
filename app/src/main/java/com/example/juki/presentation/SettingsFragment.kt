package com.example.juki.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.juki.MainActivity
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

        val activity = requireActivity() as MainActivity
        val settings = activity.gameSettings

        view.findViewById<SeekBar>(R.id.seekGameSpeed).bindTo(
                view.findViewById(R.id.tvGameSpeed),
                R.string.settings_game_speed,
                settings.gameSpeed
        ) { value ->
            activity.gameSettings =
                    activity.gameSettings.copy(gameSpeed = value)
        }

        view.findViewById<SeekBar>(R.id.seekMaxTarakani).bindTo(
                view.findViewById(R.id.tvMaxTarakani),
                R.string.settings_max_tarakani,
                settings.maxTarakani
        ) { value ->
            activity.gameSettings =
                    activity.gameSettings.copy(maxTarakani = value)
        }

        view.findViewById<SeekBar>(R.id.seekBonusInterval).bindTo(
                view.findViewById(R.id.tvBonusInterval),
                R.string.settings_bonus_interval,
                settings.bonusIntervalSec
        ) { value ->
            activity.gameSettings =
                    activity.gameSettings.copy(bonusIntervalSec = value)
        }

        view.findViewById<SeekBar>(R.id.seekRoundDuration).bindTo(
                view.findViewById(R.id.tvRoundDuration),
                R.string.settings_round_duration,
                settings.roundDurationSec
        ) { value ->
            activity.gameSettings =
                    activity.gameSettings.copy(roundDurationSec = value)
        }
    }

    private fun SeekBar.bindTo(
            label: TextView,
            labelRes: Int,
            initialValue: Int,
            onChanged: (Int) -> Unit
    ) {
        progress = initialValue.coerceIn(1, max)
        label.text = context.getString(labelRes, progress)
        onChanged(progress)

        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
            ) {
                val value = progress.coerceIn(1, max)

                if (progress != value) {
                    this@bindTo.progress = value
                    return
                }

                label.text = context.getString(labelRes, value)
                onChanged(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }
}
