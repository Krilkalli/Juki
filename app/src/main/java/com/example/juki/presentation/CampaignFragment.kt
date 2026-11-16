package com.example.juki.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.juki.MainActivity
import com.example.juki.R
import com.example.juki.domain.model.Campaign
import com.example.juki.domain.model.CampaignProgress
import com.example.juki.domain.model.User
import com.example.juki.domain.repository.CampaignRepository
import com.example.juki.game.GameActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.koin.android.ext.android.inject

class CampaignFragment : Fragment() {
    private val repository: CampaignRepository by inject()
    private var loading: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_campaign, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.campaignRetryLoad).setOnClickListener { loadProgress() }
        val menu = activity as MainActivity
        viewLifecycleOwner.lifecycleScope.launch {
            // Повторно читаем прогресс при возвращении из игры и при смене игрока.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                menu.selectedUser.collect { loadProgress() }
            }
        }
    }

    private fun loadProgress() {
        loading?.cancel()
        val view = view ?: return
        val user = (activity as? MainActivity)?.currentUser
        val playerText = view.findViewById<TextView>(R.id.campaignPlayer)
        val status = view.findViewById<TextView>(R.id.campaignStatus)
        val retry = view.findViewById<Button>(R.id.campaignRetryLoad)
        retry.visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.campaignLevels).removeAllViews()
        if (user == null) {
            playerText.setText(R.string.campaign_select_player)
            status.text = ""
            showLevels(null, emptyList())
            return
        }
        playerText.text = getString(R.string.campaign_player, "${user.surname} ${user.name}")
        status.setText(R.string.campaign_loading)
        loading = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val progress = repository.getProgress(user.id)
                val passed = Campaign.levels.count { level ->
                    progress.any { it.levelNumber == level.number && Campaign.isPassed(level, it.bestScore) }
                }
                status.text = if (passed == Campaign.levels.size) getString(R.string.campaign_complete)
                    else getString(R.string.campaign_count, passed, Campaign.levels.size)
                showLevels(user, progress)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                status.setText(R.string.campaign_error)
                retry.visibility = View.VISIBLE
            }
        }
    }

    private fun showLevels(user: User?, progress: List<CampaignProgress>) {
        val container = requireView().findViewById<LinearLayout>(R.id.campaignLevels)
        container.removeAllViews()
        Campaign.levels.forEach { level ->
            val best = progress.find { it.levelNumber == level.number }
            val unlocked = user != null && Campaign.isUnlocked(level.number, progress)
            val passed = best != null && Campaign.isPassed(level, best.bestScore)
            val details = getString(
                R.string.campaign_level_details, level.number, level.title, level.targetScore,
                level.settings.roundDurationSec, level.settings.gameSpeed, level.settings.maxTarakani
            )
            val bestText = best?.let { getString(R.string.campaign_best, it.bestScore) }
                ?: getString(R.string.campaign_no_attempts)
            container.addView(TextView(requireContext()).apply {
                text = "$details\n$bestText"
                textSize = 16f
                setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            container.addView(Button(requireContext()).apply {
                text = when {
                    !unlocked && user != null -> getString(R.string.campaign_locked_level, level.number - 1)
                    passed -> getString(R.string.campaign_replay_level, level.number)
                    else -> getString(R.string.campaign_start_level, level.number)
                }
                isEnabled = unlocked
                setOnClickListener {
                    if (user != null) GameActivity.start(requireContext(), level.settings, user, level.number)
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }
}
