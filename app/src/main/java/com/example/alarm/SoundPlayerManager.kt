package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.domain.SoundType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SoundPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    @Synchronized
    fun play(soundType: SoundType, onCompleted: (() -> Unit)? = null) {
        stopInternal(notify = false)

        val player = try {
            createPlayerFor(soundType)
        } catch (e: Exception) {
            Log.e(TAG, "Primary sound load failed, falling back to bundled raw sound", e)
            runCatching { createRawPlayer(R.raw.bildirim_kisa) }.getOrNull()
        }

        if (player == null) {
            _isPlaying.value = false
            onCompleted?.invoke()
            return
        }

        mediaPlayer = player
        player.setOnCompletionListener {
            synchronized(this@SoundPlayerManager) {
                if (mediaPlayer === it) {
                    it.release()
                    mediaPlayer = null
                    _isPlaying.value = false
                }
            }
            onCompleted?.invoke()
        }
        player.setOnErrorListener { mp, _, _ ->
            synchronized(this@SoundPlayerManager) {
                if (mediaPlayer === mp) {
                    mp.release()
                    mediaPlayer = null
                    _isPlaying.value = false
                }
            }
            onCompleted?.invoke()
            true
        }

        try {
            player.start()
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaPlayer", e)
            player.release()
            mediaPlayer = null
            _isPlaying.value = false
            onCompleted?.invoke()
        }
    }

    @Synchronized
    fun stop() {
        stopInternal(notify = true)
    }

    private fun stopInternal(notify: Boolean) {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.release() }
        }
        mediaPlayer = null
        if (notify) {
            _isPlaying.value = false
        }
    }

    private fun createPlayerFor(soundType: SoundType): MediaPlayer {
        return when (soundType) {
            SoundType.EZAN -> {
                // If user placed custom ezan_ozel.mp3 in res/raw/, prioritize it automatically
                val customResId = context.resources.getIdentifier(
                    "ezan_ozel",
                    "raw",
                    context.packageName
                )
                val targetResId = if (customResId != 0) customResId else R.raw.ezan_ornek
                createRawPlayer(targetResId)
            }

            SoundType.SHORT_NOTIFICATION -> {
                val defaultUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (defaultUri != null) {
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(context, defaultUri)
                        prepare()
                    }
                } else {
                    createRawPlayer(R.raw.bildirim_kisa)
                }
            }
        }
    }

    private fun createRawPlayer(rawResId: Int): MediaPlayer {
        val afd = context.resources.openRawResourceFd(rawResId)
            ?: error("Raw resource not found: $rawResId")
        return MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            prepare()
        }
    }

    fun hasCustomEzanFile(): Boolean {
        return context.resources.getIdentifier("ezan_ozel", "raw", context.packageName) != 0
    }

    companion object {
        private const val TAG = "SoundPlayerManager"
    }
}
