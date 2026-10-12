package com.example.juki

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.juki.domain.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.juki.domain.model.GameSettings
import com.example.juki.game.GameActivity
import com.example.juki.presentation.PagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    private val userRepository get() = (application as MyApplication).userRepository
    private val playerPreferences by lazy { getSharedPreferences("player", MODE_PRIVATE) }

    var currentUser: User? = null
        set(value) {
            field = value
            playerPreferences.edit().putLong("userId", value?.id ?: 0).apply()
        }

    var gameSettings = GameSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        gameSettings = GameSettings(
            gameSpeed = savedInstanceState?.getInt("speed") ?: 50,
            maxTarakani = savedInstanceState?.getInt("maxBugs") ?: 5,
            bonusIntervalSec = savedInstanceState?.getInt("bonus") ?: 10,
            roundDurationSec = savedInstanceState?.getInt("duration") ?: 60
        )

        val tabMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_registration)
                1 -> getString(R.string.tab_rules)
                2 -> getString(R.string.tab_authors)
                3 -> getString(R.string.tab_settings)
                4 -> getString(R.string.tab_records)
                else -> ""
            }
        }

        val startButton = findViewById<Button>(R.id.btnStartGame)
        startButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val userId = playerPreferences.getLong("userId", 0)
                currentUser = withContext(Dispatchers.IO) { userRepository.getById(userId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.players_load_error, Toast.LENGTH_LONG).show()
            }
            viewPager.adapter = PagerAdapter(this@MainActivity)
            tabMediator.attach()
            startButton.isEnabled = true
        }
        startButton.setOnClickListener {
            val user = currentUser
            if (user == null) {
                viewPager.currentItem = 0
                Toast.makeText(this, R.string.select_player_first, Toast.LENGTH_SHORT).show()
            } else {
                GameActivity.start(this, gameSettings, user)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("speed", gameSettings.gameSpeed)
        outState.putInt("maxBugs", gameSettings.maxTarakani)
        outState.putInt("bonus", gameSettings.bonusIntervalSec)
        outState.putInt("duration", gameSettings.roundDurationSec)
        super.onSaveInstanceState(outState)
    }
}
