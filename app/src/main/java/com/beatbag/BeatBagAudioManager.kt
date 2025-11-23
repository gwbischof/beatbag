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
    private val selectedSoundIds = mutableSetOf<Int>()
    private var currentSoundIndex = 0  // For backwards compatibility and initial selection

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
     * Play a random sound from the selected sounds with given intensity
     * If no sounds are selected, plays the current sound
     * @param intensity Value between 0.0 and 2.0 (normalized kick intensity)
     */
    fun playCurrentSound(intensity: Float = 1.0f) {
        if (soundLibrary.isEmpty()) {
            Log.w(TAG, "No sounds in library")
            return
        }

        // Get the sound to play
        val sound = if (selectedSoundIds.isNotEmpty()) {
            // Pick a random sound from selected sounds
            val randomId = selectedSoundIds.random()
            soundLibrary.find { it.id == randomId }
        } else {
            // Fall back to current sound if nothing selected
            soundLibrary.getOrNull(currentSoundIndex)
        }

        if (sound == null) {
            Log.w(TAG, "No valid sound to play")
            return
        }

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
     * Toggle sound selection for multi-select mode
     */
    fun toggleSoundSelection(id: Int) {
        if (selectedSoundIds.contains(id)) {
            selectedSoundIds.remove(id)
            Log.d(TAG, "Deselected sound ID: $id")
        } else {
            selectedSoundIds.add(id)
            Log.d(TAG, "Selected sound ID: $id")
        }
    }

    /**
     * Check if a sound is selected
     */
    fun isSoundSelected(id: Int): Boolean {
        return selectedSoundIds.contains(id)
    }

    /**
     * Get all selected sounds
     */
    fun getSelectedSounds(): List<Sound> {
        return soundLibrary.filter { selectedSoundIds.contains(it.id) }
    }

    /**
     * Clear all selections
     */
    fun clearSelection() {
        selectedSoundIds.clear()
        Log.d(TAG, "Cleared all sound selections")
    }

    /**
     * Get count of selected sounds
     */
    fun getSelectedCount(): Int {
        return selectedSoundIds.size
    }

    /**
     * Release resources
     */
    fun release() {
        soundPool.release()
        soundLibrary.clear()
        selectedSoundIds.clear()
        Log.d(TAG, "Audio manager released")
    }
}
