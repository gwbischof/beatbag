package com.beatbag

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var bleManager: BleManager
    private lateinit var kickDetector: KickDetector
    private lateinit var audioManager: BeatBagAudioManager

    private lateinit var statusText: TextView
    private lateinit var dataRateText: TextView
    private lateinit var connectButton: Button
    private lateinit var kickIndicator: View
    private lateinit var kickText: TextView
    private lateinit var currentSoundText: TextView
    private lateinit var soundLibraryGrid: RecyclerView
    private lateinit var addSoundButton: Button
    private lateinit var upperThresholdSlider: SeekBar
    private lateinit var lowerThresholdSlider: SeekBar
    private lateinit var upperThresholdValue: TextView
    private lateinit var lowerThresholdValue: TextView
    private lateinit var collectionSpinner: Spinner
    private lateinit var addCollectionButton: Button

    private lateinit var soundAdapter: SoundAdapter
    private val foundDevices = mutableListOf<Pair<String, String>>()
    private var isConnected = false

    // Data rate tracking
    private var packetCount = 0
    private var lastRateUpdate = System.currentTimeMillis()

    // File picker for adding custom sounds
    private val pickAudioFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { addCustomSound(it) }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize components
        bleManager = BleManager(this)
        kickDetector = KickDetector()
        audioManager = BeatBagAudioManager(this)

        // Initialize views
        statusText = findViewById(R.id.statusText)
        dataRateText = findViewById(R.id.dataRateText)
        connectButton = findViewById(R.id.connectButton)
        kickIndicator = findViewById(R.id.kickIndicator)
        kickText = findViewById(R.id.kickText)
        currentSoundText = findViewById(R.id.currentSoundText)
        soundLibraryGrid = findViewById(R.id.soundLibraryGrid)
        addSoundButton = findViewById(R.id.addSoundButton)
        upperThresholdSlider = findViewById(R.id.upperThresholdSlider)
        lowerThresholdSlider = findViewById(R.id.lowerThresholdSlider)
        upperThresholdValue = findViewById(R.id.upperThresholdValue)
        lowerThresholdValue = findViewById(R.id.lowerThresholdValue)
        collectionSpinner = findViewById(R.id.collectionSpinner)
        addCollectionButton = findViewById(R.id.addCollectionButton)

        // Set up RecyclerView
        soundAdapter = SoundAdapter { sound ->
            // Toggle selection instead of selecting a single sound
            audioManager.toggleSoundSelection(sound.id)
            updateCurrentSoundText()
            soundAdapter.notifyDataSetChanged()
        }
        soundLibraryGrid.layoutManager = GridLayoutManager(this, 3)
        soundLibraryGrid.adapter = soundAdapter

        // Load default sounds (will be added later)
        loadDefaultSounds()

        // Load saved configuration
        audioManager.loadConfiguration()

        // Set up collection spinner
        setupCollectionSpinner()

        // Set up listeners
        setupListeners()

        // Request permissions
        checkAndRequestPermissions()
    }

    private fun setupListeners() {
        connectButton.setOnClickListener {
            if (isConnected) {
                disconnect()
            } else {
                foundDevices.clear()
                bleManager.startScan()
                connectButton.text = getString(R.string.scanning)
                connectButton.isEnabled = false

                // Stop scanning after 10 seconds
                lifecycleScope.launch {
                    delay(10000)
                    bleManager.stopScan()
                    if (!isConnected) {
                        connectButton.text = getString(R.string.scan_for_sensor)
                        connectButton.isEnabled = true
                        showDeviceSelectionDialog()
                    }
                }
            }
        }

        addSoundButton.setOnClickListener {
            pickAudioFile.launch("audio/*")
        }

        addCollectionButton.setOnClickListener {
            showAddCollectionDialog()
        }

        // BLE callbacks
        bleManager.setCallback(object : BleManager.BleCallback {
            override fun onScanResult(deviceName: String, deviceAddress: String) {
                runOnUiThread {
                    if (!foundDevices.any { it.second == deviceAddress }) {
                        foundDevices.add(Pair(deviceName, deviceAddress))
                    }
                }
            }

            override fun onConnected() {
                runOnUiThread {
                    isConnected = true
                    statusText.text = getString(R.string.connected)
                    statusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.sensor_connected))
                    connectButton.text = getString(R.string.disconnect)
                    connectButton.isEnabled = true
                }
            }

            override fun onDisconnected() {
                runOnUiThread {
                    isConnected = false
                    statusText.text = getString(R.string.disconnected)
                    statusText.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.sensor_disconnected))
                    connectButton.text = getString(R.string.scan_for_sensor)
                    kickIndicator.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.sensor_disconnected))
                    kickText.text = "Ready"
                    dataRateText.text = "0 Hz"
                    packetCount = 0
                    lastRateUpdate = System.currentTimeMillis()
                }
            }

            override fun onSensorData(data: BleManager.SensorData) {
                // Process kick detection
                kickDetector.processSensorData(data.adjustedAccel)

                // Update data rate
                packetCount++
                val now = System.currentTimeMillis()
                val elapsed = now - lastRateUpdate

                // Update display every second
                if (elapsed >= 1000) {
                    val rate = (packetCount * 1000.0 / elapsed).toInt()
                    runOnUiThread {
                        dataRateText.text = "$rate Hz"
                    }
                    packetCount = 0
                    lastRateUpdate = now
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        })

        // Kick detector callback
        kickDetector.setListener(object : KickDetector.KickListener {
            override fun onKickDetected(intensity: Float) {
                runOnUiThread {
                    // Play sound
                    audioManager.playCurrentSound(intensity)

                    // Visual feedback
                    kickIndicator.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.kick_active))
                    kickText.text = getString(R.string.kick_detected)

                    // Reset indicator after 200ms
                    lifecycleScope.launch {
                        delay(200)
                        kickIndicator.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.sensor_connected))
                        kickText.text = "Ready"
                    }
                }
            }
        })

        // Threshold sliders
        upperThresholdSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val threshold = progress / 10.0f  // 0.0 - 4.0g
                kickDetector.upperThreshold = threshold
                upperThresholdValue.text = String.format("%.1fg", threshold)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        lowerThresholdSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val threshold = progress / 20.0f  // 0.0 - 1.0g
                kickDetector.lowerThreshold = threshold
                lowerThresholdValue.text = String.format("%.2fg", threshold)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun loadDefaultSounds() {
        // Load General collection sounds
        audioManager.addSoundFromResource("Click", R.raw.kick1, "General")
        audioManager.addSoundFromResource("Impact", R.raw.kick2, "General")
        audioManager.addSoundFromResource("Beep", R.raw.kick3, "General")
        audioManager.addSoundFromResource("Zap", R.raw.kick4, "General")
        audioManager.addSoundFromResource("Drip", R.raw.kick5, "General")
        audioManager.addSoundFromResource("Gear", R.raw.kick6, "General")
        audioManager.addSoundFromResource("Steam", R.raw.kick7, "General")
        audioManager.addSoundFromResource("Button", R.raw.kick8, "General")
        audioManager.addSoundFromResource("Zoom", R.raw.kick9, "General")
        audioManager.addSoundFromResource("Mech", R.raw.kick10, "General")
        audioManager.addSoundFromResource("Switch", R.raw.kick11, "General")
        audioManager.addSoundFromResource("Bloop", R.raw.kick12, "General")
        audioManager.addSoundFromResource("Pop", R.raw.kick13, "General")
        audioManager.addSoundFromResource("Thump", R.raw.kick14, "General")

        // Load Farts collection sounds
        audioManager.addSoundFromResource("Fart 1", R.raw.fart_1, "Farts")
        audioManager.addSoundFromResource("Fart 2", R.raw.fart_2, "Farts")
        audioManager.addSoundFromResource("Fart 3", R.raw.fart_3, "Farts")
        audioManager.addSoundFromResource("Fart 4", R.raw.fart_4, "Farts")
        audioManager.addSoundFromResource("Fart 5", R.raw.fart_5, "Farts")
        audioManager.addSoundFromResource("Fart 7", R.raw.fart_7, "Farts")
        audioManager.addSoundFromResource("Fart 8", R.raw.fart_8, "Farts")
        audioManager.addSoundFromResource("Fart 9", R.raw.fart_9, "Farts")
        audioManager.addSoundFromResource("Fart 10", R.raw.fart_10, "Farts")
        audioManager.addSoundFromResource("Fart Combo", R.raw.fart_combo, "Farts")
        audioManager.addSoundFromResource("Echo Fart", R.raw.fart_echo, "Farts")
        audioManager.addSoundFromResource("Small Fart", R.raw.fart_small, "Farts")

        updateSoundLibraryUI()
    }

    private fun updateSoundLibraryUI() {
        soundAdapter.updateSounds(audioManager.getAllSounds())
        updateCurrentSoundText()
    }

    private fun updateCurrentSoundText() {
        val selectedCount = audioManager.getSelectedCount()
        currentSoundText.text = if (selectedCount > 0) {
            "Random ($selectedCount selected)"
        } else {
            audioManager.getCurrentSound()?.name ?: "None"
        }
    }

    private fun setupCollectionSpinner() {
        val collections = audioManager.getCollectionNames()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, collections)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        collectionSpinner.adapter = adapter

        // Set listener for collection changes
        collectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCollection = collections[position]
                audioManager.setCurrentCollection(selectedCollection)
                updateSoundLibraryUI()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun showAddCollectionDialog() {
        val input = EditText(this)
        input.hint = "Collection name"

        AlertDialog.Builder(this)
            .setTitle("New Collection")
            .setMessage("Enter a name for the new collection:")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val collectionName = input.text.toString().trim()
                if (collectionName.isEmpty()) {
                    Toast.makeText(this, "Collection name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (audioManager.createCollection(collectionName)) {
                    // Save configuration
                    audioManager.saveConfiguration()

                    // Refresh the spinner
                    setupCollectionSpinner()

                    // Select the new collection
                    val collections = audioManager.getCollectionNames()
                    val newIndex = collections.indexOf(collectionName)
                    if (newIndex != -1) {
                        collectionSpinner.setSelection(newIndex)
                    }

                    Toast.makeText(this, "Created collection: $collectionName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Collection already exists", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeviceSelectionDialog() {
        if (foundDevices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Sensors Found")
                .setMessage(getString(R.string.sensor_not_found))
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val deviceNames = foundDevices.map { "${it.first} (${it.second})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Sensor")
            .setItems(deviceNames) { _, which ->
                val (_, address) = foundDevices[which]
                bleManager.connect(address)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun disconnect() {
        bleManager.disconnect()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
        audioManager.release()
    }

    private fun addCustomSound(uri: Uri) {
        try {
            // Get the file name from content resolver
            val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "custom_sound_${System.currentTimeMillis()}.wav"

            // Remove file extension for the sound name
            val soundName = fileName.substringBeforeLast(".")

            // Copy file to app's internal storage
            val soundsDir = File(filesDir, "custom_sounds")
            if (!soundsDir.exists()) {
                soundsDir.mkdirs()
            }

            val destFile = File(soundsDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Load the sound into the audio manager
            audioManager.addSoundFromFile(soundName, destFile.absolutePath)
            audioManager.saveConfiguration()
            updateSoundLibraryUI()

            runOnUiThread {
                Toast.makeText(this, "Added sound: $soundName", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error adding sound: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // RecyclerView Adapter for sound library
    inner class SoundAdapter(
        private val onSoundClick: (BeatBagAudioManager.Sound) -> Unit
    ) : RecyclerView.Adapter<SoundAdapter.SoundViewHolder>() {

        private var sounds = listOf<BeatBagAudioManager.Sound>()

        fun updateSounds(newSounds: List<BeatBagAudioManager.Sound>) {
            sounds = newSounds
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SoundViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sound, parent, false)
            return SoundViewHolder(view)
        }

        override fun onBindViewHolder(holder: SoundViewHolder, position: Int) {
            holder.bind(sounds[position])
        }

        override fun getItemCount() = sounds.size

        inner class SoundViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val soundName: TextView = itemView.findViewById(R.id.soundName)

            fun bind(sound: BeatBagAudioManager.Sound) {
                soundName.text = sound.name

                // Show selected state (purple for selected, white for unselected)
                if (audioManager.isSoundSelected(sound.id)) {
                    (itemView as com.google.android.material.card.MaterialCardView)
                        .setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.purple_200))
                } else {
                    (itemView as com.google.android.material.card.MaterialCardView)
                        .setCardBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                }

                itemView.setOnClickListener {
                    onSoundClick(sound)
                }
            }
        }
    }
}
