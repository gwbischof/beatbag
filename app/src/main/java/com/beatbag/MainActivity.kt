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
import android.widget.*
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

    private lateinit var soundAdapter: SoundAdapter
    private val foundDevices = mutableListOf<Pair<String, String>>()
    private var isConnected = false

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

        // Set up RecyclerView
        soundAdapter = SoundAdapter { sound ->
            audioManager.selectSound(sound.id)
            currentSoundText.text = sound.name
            soundAdapter.notifyDataSetChanged()
        }
        soundLibraryGrid.layoutManager = GridLayoutManager(this, 3)
        soundLibraryGrid.adapter = soundAdapter

        // Load default sounds (will be added later)
        loadDefaultSounds()

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
                }
            }

            override fun onSensorData(data: BleManager.SensorData) {
                // Process kick detection
                kickDetector.processSensorData(data.adjustedAccel)
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
        // Add default sounds from resources
        audioManager.addSoundFromResource("Click", R.raw.kick1)
        audioManager.addSoundFromResource("Impact", R.raw.kick2)
        audioManager.addSoundFromResource("Beep", R.raw.kick3)
        audioManager.addSoundFromResource("Zap", R.raw.kick4)
        audioManager.addSoundFromResource("Drip", R.raw.kick5)
        audioManager.addSoundFromResource("Gear", R.raw.kick6)
        audioManager.addSoundFromResource("Steam", R.raw.kick7)
        audioManager.addSoundFromResource("Button", R.raw.kick8)
        audioManager.addSoundFromResource("Zoom", R.raw.kick9)
        audioManager.addSoundFromResource("Mech", R.raw.kick10)
        audioManager.addSoundFromResource("Switch", R.raw.kick11)
        audioManager.addSoundFromResource("Bloop", R.raw.kick12)
        audioManager.addSoundFromResource("Pop", R.raw.kick13)
        audioManager.addSoundFromResource("Thump", R.raw.kick14)

        updateSoundLibraryUI()
    }

    private fun updateSoundLibraryUI() {
        soundAdapter.updateSounds(audioManager.getAllSounds())
        val currentSound = audioManager.getCurrentSound()
        currentSoundText.text = currentSound?.name ?: "None"
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

                val currentSound = audioManager.getCurrentSound()
                if (currentSound?.id == sound.id) {
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
