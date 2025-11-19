package com.beatbag

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * Manages sound library and audio playback for kick sounds
 */
class BeatBagAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "BeatBagAudioManager"
        private const val MAX_STREAMS = 5
    }

    data class Sound(
        val id: Int,
        val name: String,
        val resourceId: Int? = null,
        val filePath: String? = null,
        var soundId: Int = -1  // SoundPool sound ID
    )

    private val soundPool: SoundPool
    private val soundLibrary = mutableListOf<Sound>()
    private var currentSoundIndex = 0

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                Log.d(TAG, "Sound loaded: $sampleId")
            } else {
                Log.e(TAG, "Failed to load sound: $sampleId")
            }
        }
    }

    /**
     * Add a sound from app resources
     */
    fun addSoundFromResource(name: String, resourceId: Int): Int {
        val soundId = soundPool.load(context, resourceId, 1)
        val sound = Sound(
            id = soundLibrary.size,
            name = name,
            resourceId = resourceId,
            soundId = soundId
        )
        soundLibrary.add(sound)
        Log.d(TAG, "Added sound from resource: $name")
        return sound.id
    }

    /**
     * Add a sound from external file path
     */
    fun addSoundFromFile(name: String, filePath: String): Int {
        val soundId = soundPool.load(filePath, 1)
        val sound = Sound(
            id = soundLibrary.size,
            name = name,
            filePath = filePath,
            soundId = soundId
        )
        soundLibrary.add(sound)
        Log.d(TAG, "Added sound from file: $name at $filePath")
        return sound.id
    }

    /**
     * Play the currently selected sound with given intensity
     * @param intensity Value between 0.0 and 2.0 (normalized kick intensity)
     */
    fun playCurrentSound(intensity: Float = 1.0f) {
        if (soundLibrary.isEmpty()) {
            Log.w(TAG, "No sounds in library")
            return
        }

        val sound = soundLibrary[currentSoundIndex]
        if (sound.soundId == -1) {
            Log.w(TAG, "Sound not loaded: ${sound.name}")
            return
        }

        // Convert intensity to volume (0.0 - 1.0)
        val volume = (intensity / 2.0f).coerceIn(0.3f, 1.0f)

        soundPool.play(
            sound.soundId,
            volume,  // left volume
            volume,  // right volume
            1,       // priority
            0,       // loop (0 = no loop)
            1.0f     // playback rate
        )

        Log.d(TAG, "Playing sound: ${sound.name} with volume: $volume")
    }

    /**
     * Select a sound by index
     */
    fun selectSound(index: Int) {
        if (index in soundLibrary.indices) {
            currentSoundIndex = index
            Log.d(TAG, "Selected sound: ${soundLibrary[index].name}")
        } else {
            Log.w(TAG, "Invalid sound index: $index")
        }
    }

    /**
     * Get current sound
     */
    fun getCurrentSound(): Sound? {
        return if (soundLibrary.isNotEmpty()) {
            soundLibrary[currentSoundIndex]
        } else {
            null
        }
    }

    /**
     * Get all sounds in library
     */
    fun getAllSounds(): List<Sound> = soundLibrary.toList()

    /**
     * Remove a sound from the library
     */
    fun removeSound(id: Int) {
        val index = soundLibrary.indexOfFirst { it.id == id }
        if (index != -1) {
            val sound = soundLibrary[index]
            if (sound.soundId != -1) {
                soundPool.unload(sound.soundId)
            }
            soundLibrary.removeAt(index)

            // Adjust current index if needed
            if (currentSoundIndex >= soundLibrary.size) {
                currentSoundIndex = maxOf(0, soundLibrary.size - 1)
            }

            Log.d(TAG, "Removed sound: ${sound.name}")
        }
    }

    /**
     * Release resources
     */
    fun release() {
        soundPool.release()
        soundLibrary.clear()
        Log.d(TAG, "Audio manager released")
    }
}
