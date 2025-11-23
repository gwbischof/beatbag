package com.beatbag

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Manages sound library and audio playback for kick sounds
 */
class BeatBagAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "BeatBagAudioManager"
        private const val MAX_STREAMS = 5
        private const val CONFIG_FILE = "audio_config.json"
    }

    // Serializable data classes for persistence (excluding runtime SoundPool IDs)
    data class SavedSound(
        val id: Int,
        val name: String,
        val resourceId: Int? = null,
        val filePath: String? = null
    )

    data class SavedCollection(
        val name: String,
        val sounds: List<SavedSound>
    )

    data class SavedConfiguration(
        val collections: List<SavedCollection>,
        val currentCollectionName: String,
        val selectedSoundIds: Set<Int>,
        val currentSoundIndex: Int
    )

    data class Sound(
        val id: Int,
        val name: String,
        val resourceId: Int? = null,
        val filePath: String? = null,
        var soundId: Int = -1  // SoundPool sound ID
    )

    data class SoundCollection(
        val name: String,
        val sounds: MutableList<Sound> = mutableListOf()
    )

    private val soundPool: SoundPool
    private val collections = mutableMapOf<String, SoundCollection>()
    private var currentCollectionName = "General"
    private val selectedSoundIds = mutableSetOf<Int>()
    private var currentSoundIndex = 0  // For backwards compatibility and initial selection
    private var nextSoundId = 0  // Global sound ID counter

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
     * Add a sound from app resources to a specific collection
     */
    fun addSoundFromResource(name: String, resourceId: Int, collectionName: String = currentCollectionName): Int {
        val soundId = soundPool.load(context, resourceId, 1)
        val sound = Sound(
            id = nextSoundId++,
            name = name,
            resourceId = resourceId,
            soundId = soundId
        )
        getOrCreateCollection(collectionName).sounds.add(sound)
        Log.d(TAG, "Added sound from resource: $name to collection: $collectionName")
        return sound.id
    }

    /**
     * Add a sound from external file path to a specific collection
     */
    fun addSoundFromFile(name: String, filePath: String, collectionName: String = currentCollectionName): Int {
        val soundId = soundPool.load(filePath, 1)
        val sound = Sound(
            id = nextSoundId++,
            name = name,
            filePath = filePath,
            soundId = soundId
        )
        getOrCreateCollection(collectionName).sounds.add(sound)
        Log.d(TAG, "Added sound from file: $name at $filePath to collection: $collectionName")
        return sound.id
    }

    /**
     * Get or create a collection by name
     */
    private fun getOrCreateCollection(name: String): SoundCollection {
        return collections.getOrPut(name) { SoundCollection(name) }
    }

    /**
     * Play a random sound from the selected sounds with given intensity
     * If no sounds are selected, plays the current sound
     * @param intensity Value between 0.0 and 2.0 (normalized kick intensity)
     */
    fun playCurrentSound(intensity: Float = 1.0f) {
        val currentCollection = collections[currentCollectionName]
        if (currentCollection == null || currentCollection.sounds.isEmpty()) {
            Log.w(TAG, "No sounds in current collection")
            return
        }

        // Get the sound to play
        val sound = if (selectedSoundIds.isNotEmpty()) {
            // Pick a random sound from selected sounds (filter by current collection)
            val selectedInCollection = currentCollection.sounds.filter { selectedSoundIds.contains(it.id) }
            if (selectedInCollection.isNotEmpty()) {
                selectedInCollection.random()
            } else {
                currentCollection.sounds.getOrNull(currentSoundIndex)
            }
        } else {
            // Fall back to current sound if nothing selected
            currentCollection.sounds.getOrNull(currentSoundIndex)
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
     * Select a sound by index in current collection
     */
    fun selectSound(index: Int) {
        val currentCollection = collections[currentCollectionName]
        if (currentCollection != null && index in currentCollection.sounds.indices) {
            currentSoundIndex = index
            Log.d(TAG, "Selected sound: ${currentCollection.sounds[index].name}")
        } else {
            Log.w(TAG, "Invalid sound index: $index")
        }
    }

    /**
     * Get current sound from current collection
     */
    fun getCurrentSound(): Sound? {
        val currentCollection = collections[currentCollectionName]
        return if (currentCollection != null && currentCollection.sounds.isNotEmpty()) {
            currentCollection.sounds.getOrNull(currentSoundIndex)
        } else {
            null
        }
    }

    /**
     * Get all sounds in current collection
     */
    fun getAllSounds(): List<Sound> {
        return collections[currentCollectionName]?.sounds?.toList() ?: emptyList()
    }

    /**
     * Switch to a different collection
     */
    fun setCurrentCollection(name: String) {
        if (collections.containsKey(name)) {
            currentCollectionName = name
            currentSoundIndex = 0
            selectedSoundIds.clear()
            Log.d(TAG, "Switched to collection: $name")
        } else {
            Log.w(TAG, "Collection not found: $name")
        }
    }

    /**
     * Get all collection names
     */
    fun getCollectionNames(): List<String> {
        return collections.keys.toList()
    }

    /**
     * Get current collection name
     */
    fun getCurrentCollectionName(): String {
        return currentCollectionName
    }

    /**
     * Create a new empty collection
     * @return true if created successfully, false if collection already exists
     */
    fun createCollection(name: String): Boolean {
        if (collections.containsKey(name)) {
            Log.w(TAG, "Collection already exists: $name")
            return false
        }
        collections[name] = SoundCollection(name)
        Log.d(TAG, "Created new collection: $name")
        return true
    }

    /**
     * Remove a sound from the current collection
     */
    fun removeSound(id: Int) {
        val currentCollection = collections[currentCollectionName] ?: return
        val index = currentCollection.sounds.indexOfFirst { it.id == id }
        if (index != -1) {
            val sound = currentCollection.sounds[index]
            if (sound.soundId != -1) {
                soundPool.unload(sound.soundId)
            }
            currentCollection.sounds.removeAt(index)

            // Adjust current index if needed
            if (currentSoundIndex >= currentCollection.sounds.size) {
                currentSoundIndex = maxOf(0, currentCollection.sounds.size - 1)
            }

            // Remove from selection if selected
            selectedSoundIds.remove(id)

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
     * Get all selected sounds from current collection
     */
    fun getSelectedSounds(): List<Sound> {
        val currentCollection = collections[currentCollectionName]
        return currentCollection?.sounds?.filter { selectedSoundIds.contains(it.id) } ?: emptyList()
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
     * Save current configuration to file
     */
    fun saveConfiguration() {
        try {
            val savedCollections = collections.map { (_, collection) ->
                SavedCollection(
                    name = collection.name,
                    sounds = collection.sounds.map { sound ->
                        SavedSound(
                            id = sound.id,
                            name = sound.name,
                            resourceId = sound.resourceId,
                            filePath = sound.filePath
                        )
                    }
                )
            }

            val config = SavedConfiguration(
                collections = savedCollections,
                currentCollectionName = currentCollectionName,
                selectedSoundIds = selectedSoundIds,
                currentSoundIndex = currentSoundIndex
            )

            val gson = Gson()
            val json = gson.toJson(config)
            val configFile = File(context.filesDir, CONFIG_FILE)
            configFile.writeText(json)

            Log.d(TAG, "Configuration saved")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save configuration", e)
        }
    }

    /**
     * Load configuration from file
     * Should be called after default sounds are loaded
     */
    fun loadConfiguration() {
        try {
            val configFile = File(context.filesDir, CONFIG_FILE)
            if (!configFile.exists()) {
                Log.d(TAG, "No saved configuration found")
                return
            }

            val json = configFile.readText()
            val gson = Gson()
            val config = gson.fromJson(json, SavedConfiguration::class.java)

            // Clear existing custom collections (keep defaults if they exist)
            val defaultCollectionNames = collections.keys.toSet()

            // Load saved collections
            config.collections.forEach { savedCollection ->
                // Skip if this is a default collection that already exists
                if (defaultCollectionNames.contains(savedCollection.name)) {
                    return@forEach
                }

                // Create the collection
                val collection = getOrCreateCollection(savedCollection.name)

                // Load sounds for this collection
                savedCollection.sounds.forEach { savedSound ->
                    val soundId = when {
                        savedSound.resourceId != null -> {
                            soundPool.load(context, savedSound.resourceId, 1)
                        }
                        savedSound.filePath != null && File(savedSound.filePath).exists() -> {
                            soundPool.load(savedSound.filePath, 1)
                        }
                        else -> {
                            Log.w(TAG, "Sound file not found: ${savedSound.name}")
                            -1
                        }
                    }

                    if (soundId != -1) {
                        val sound = Sound(
                            id = savedSound.id,
                            name = savedSound.name,
                            resourceId = savedSound.resourceId,
                            filePath = savedSound.filePath,
                            soundId = soundId
                        )
                        collection.sounds.add(sound)

                        // Update nextSoundId to avoid conflicts
                        if (savedSound.id >= nextSoundId) {
                            nextSoundId = savedSound.id + 1
                        }
                    }
                }
            }

            // Restore state
            if (collections.containsKey(config.currentCollectionName)) {
                currentCollectionName = config.currentCollectionName
            }
            currentSoundIndex = config.currentSoundIndex
            selectedSoundIds.clear()
            selectedSoundIds.addAll(config.selectedSoundIds)

            Log.d(TAG, "Configuration loaded: ${config.collections.size} collections")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load configuration", e)
        }
    }

    /**
     * Release resources
     */
    fun release() {
        soundPool.release()
        collections.clear()
        selectedSoundIds.clear()
        Log.d(TAG, "Audio manager released")
    }
}
