package com.example.juki.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.juki.R

class BugSoundPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var soundId = 0
    private var streamId = 0
    private var loaded = false
    private var released = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (!released && sampleId == soundId) loaded = status == 0
        }
        soundId = soundPool.load(context, R.raw.bug_scream, 1)
    }

    fun play() {
        if (!loaded || released) return
        stop()
        streamId = soundPool.play(soundId, 0.65f, 0.65f, 1, 0, 1f)
    }

    fun stop() {
        if (released) return
        if (streamId != 0) soundPool.stop(streamId)
        streamId = 0
    }

    fun release() {
        if (released) return
        stop()
        released = true
        soundPool.setOnLoadCompleteListener(null)
        soundPool.release()
    }
}
