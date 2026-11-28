package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.R
import com.example.domain.SoundType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
                val importedFile = customAudioFile()
                if (importedFile.exists() && importedFile.length() > 0L) {
                    createFilePlayer(importedFile)
                } else {
                    // If developer placed custom ezan_ozel.mp3 in res/raw/, prioritize it next
                    val customResId = context.resources.getIdentifier(
                        "ezan_ozel",
                        "raw",
                        context.packageName
                    )
                    val targetResId = if (customResId != 0) customResId else R.raw.ezan_ornek
                    createRawPlayer(targetResId)
                }
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

    private fun createFilePlayer(file: File): MediaPlayer {
        return MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(file.absolutePath)
            prepare()
        }
    }

    suspend fun importCustomAudio(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "ozel_ezan.mp3"
            val tempFile = File(context.filesDir, "custom_ezan_audio.tmp")
            val targetFile = customAudioFile()

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Seçilen ses dosyası açılamadı.")

            if (tempFile.length() <= 0L) {
                tempFile.delete()
                error("Seçilen dosya boş görünüyor.")
            }

            // Validate that Android MediaPlayer can decode this audio file
            val testPlayer = MediaPlayer()
            try {
                testPlayer.setDataSource(tempFile.absolutePath)
                testPlayer.prepare()
            } catch (e: Exception) {
                tempFile.delete()
                throw IllegalArgumentException("Bu ses formatı desteklenmiyor. Lütfen geçerli bir MP3, WAV veya M4A dosyası seçin.", e)
            } finally {
                runCatching { testPlayer.release() }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            displayName
        }
    }

    fun clearCustomAudio() {
        stop()
        val file = customAudioFile()
        if (file.exists()) {
            file.delete()
        }
    }

    fun hasCustomAudioFile(): Boolean {
        val imported = customAudioFile()
        return (imported.exists() && imported.length() > 0L) || hasBundledCustomEzanFile()
    }

    fun hasBundledCustomEzanFile(): Boolean {
        return context.resources.getIdentifier("ezan_ozel", "raw", context.packageName) != 0
    }

    private fun customAudioFile(): File = File(context.filesDir, CUSTOM_AUDIO_FILENAME)

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "SoundPlayerManager"
        private const val CUSTOM_AUDIO_FILENAME = "custom_ezan_audio"
    }
}
