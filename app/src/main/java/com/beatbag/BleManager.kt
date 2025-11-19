package com.beatbag

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.sqrt

/**
 * Manages BLE communication with WT9011DCL motion sensor
 */
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        private val NOTIFY_UUID = UUID.fromString("0000ffe4-0000-1000-8000-00805f9a34fb")
        private val WRITE_UUID = UUID.fromString("0000ffe9-0000-1000-8000-00805f9a34fb")
        private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9a34fb")

        // Sensor configuration commands
        private val CMD_UNLOCK = byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x69.toByte(), 0x88.toByte(), 0xB5.toByte())
        private val CMD_SET_RATE_100HZ = byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x03.toByte(), 0x09.toByte(), 0x00.toByte())
        private val CMD_SAVE_CONFIG = byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())

        // Scaling constants
        private const val ACCEL_SCALE = 16.0f / 32768.0f  // ±16g range
        private const val GYRO_SCALE = 2000.0f / 32768.0f  // ±2000°/s range
        private const val ANGLE_SCALE = 180.0f / 32768.0f  // ±180° range
        private const val GRAVITY_OFFSET = 2.09f  // Baseline to subtract from magnitude
    }

    data class SensorData(
        val accelX: Float = 0f,
        val accelY: Float = 0f,
        val accelZ: Float = 0f,
        val gyroX: Float = 0f,
        val gyroY: Float = 0f,
        val gyroZ: Float = 0f,
        val roll: Float = 0f,
        val pitch: Float = 0f,
        val yaw: Float = 0f,
        val accelMagnitude: Float = 0f,
        val adjustedAccel: Float = 0f
    )

    interface BleCallback {
        fun onScanResult(deviceName: String, deviceAddress: String)
        fun onConnected()
        fun onDisconnected()
        fun onSensorData(data: SensorData)
        fun onError(message: String)
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var callback: BleCallback? = null
    private var isScanning = false
    private var configStep = 0
    private var configService: BluetoothGattService? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: return
            if (deviceName.contains("WT901", ignoreCase = true)) {
                Log.d(TAG, "Found sensor: $deviceName at ${result.device.address}")
                callback?.onScanResult(deviceName, result.device.address)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            callback?.onError("Bluetooth scan failed")
            isScanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    callback?.onDisconnected()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered, configuring sensor...")
                configureSensor(gatt)
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                callback?.onError("Failed to discover services")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "Characteristic write complete: $status, step: $configStep")
            if (status == BluetoothGatt.GATT_SUCCESS && configService != null) {
                continueConfiguration(gatt)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d(TAG, "Descriptor write complete: $status, step: $configStep")
            if (status == BluetoothGatt.GATT_SUCCESS && configService != null) {
                continueConfiguration(gatt)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == NOTIFY_UUID) {
                val data = characteristic.value
                if (data != null) {
                    decodePacket(data)
                }
            }
        }
    }

    fun setCallback(callback: BleCallback) {
        this.callback = callback
    }

    fun startScan() {
        if (bluetoothAdapter == null) {
            callback?.onError("Bluetooth not available")
            return
        }

        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            callback?.onError("BLE scanner not available")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
        isScanning = true
        Log.d(TAG, "Started BLE scan")
    }

    fun stopScan() {
        if (!isScanning) return

        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        Log.d(TAG, "Stopped BLE scan")
    }

    fun connect(deviceAddress: String) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            callback?.onError("Device not found")
            return
        }

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        Log.d(TAG, "Connecting to $deviceAddress...")
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private fun configureSensor(gatt: BluetoothGatt) {
        // Log all available services for debugging
        Log.d(TAG, "Available services:")
        for (service in gatt.services) {
            Log.d(TAG, "  Service UUID: ${service.uuid}")
            for (char in service.characteristics) {
                Log.d(TAG, "    Characteristic: ${char.uuid}")
            }
        }

        val service = gatt.getService(SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Service not found. Looking for: $SERVICE_UUID")
            // Try to find any service with the notify and write characteristics
            for (s in gatt.services) {
                val notifyChar = s.getCharacteristic(NOTIFY_UUID)
                val writeChar = s.getCharacteristic(WRITE_UUID)
                if (notifyChar != null && writeChar != null) {
                    Log.d(TAG, "Found characteristics in service: ${s.uuid}")
                    configureSensorWithService(gatt, s)
                    return
                }
            }
            callback?.onError("Sensor service not found")
            return
        }
        configureSensorWithService(gatt, service)
    }

    private fun configureSensorWithService(gatt: BluetoothGatt, service: BluetoothGattService) {
        val writeChar = service.getCharacteristic(WRITE_UUID)
        val notifyChar = service.getCharacteristic(NOTIFY_UUID)

        if (writeChar == null || notifyChar == null) {
            Log.e(TAG, "Required characteristics not found")
            callback?.onError("Sensor characteristics not found")
            return
        }

        configService = service
        configStep = 0
        continueConfiguration(gatt)
    }

    private fun continueConfiguration(gatt: BluetoothGatt) {
        val service = configService ?: return
        val writeChar = service.getCharacteristic(WRITE_UUID)
        val notifyChar = service.getCharacteristic(NOTIFY_UUID)

        if (writeChar == null || notifyChar == null) return

        when (configStep) {
            0 -> {
                // Step 0: Unlock sensor
                Log.d(TAG, "Step 0: Unlocking sensor")
                writeChar.value = CMD_UNLOCK
                gatt.writeCharacteristic(writeChar)
                configStep++
            }
            1 -> {
                // Step 1: Set data rate to 100Hz
                Log.d(TAG, "Step 1: Setting 100Hz rate")
                writeChar.value = CMD_SET_RATE_100HZ
                gatt.writeCharacteristic(writeChar)
                configStep++
            }
            2 -> {
                // Step 2: Save configuration
                Log.d(TAG, "Step 2: Saving config")
                writeChar.value = CMD_SAVE_CONFIG
                gatt.writeCharacteristic(writeChar)
                configStep++
            }
            3 -> {
                // Step 3: Enable notifications
                Log.d(TAG, "Step 3: Enabling notifications")
                gatt.setCharacteristicNotification(notifyChar, true)
                val descriptor = notifyChar.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    configStep++
                } else {
                    Log.e(TAG, "Descriptor not found")
                    callback?.onError("Failed to enable notifications")
                }
            }
            4 -> {
                // Configuration complete
                Log.d(TAG, "Sensor configured and ready")
                callback?.onConnected()
                configService = null
                configStep = 0
            }
        }
    }

    private fun decodePacket(data: ByteArray) {
        // BLE may send multiple concatenated packets
        var offset = 0
        while (offset + 20 <= data.size) {
            // Check for valid packet header (0x55 0x61)
            if (data[offset].toInt() and 0xFF != 0x55 ||
                data[offset + 1].toInt() and 0xFF != 0x61) {
                offset++
                continue
            }

            try {
                // Parse packet data (9 int16 values, little-endian)
                val buffer = ByteBuffer.wrap(data, offset + 2, 18)
                buffer.order(ByteOrder.LITTLE_ENDIAN)

                val ax = buffer.short
                val ay = buffer.short
                val az = buffer.short
                val wx = buffer.short
                val wy = buffer.short
                val wz = buffer.short
                val roll = buffer.short
                val pitch = buffer.short
                val yaw = buffer.short

                // Scale to physical units
                val accelX = ax * ACCEL_SCALE
                val accelY = ay * ACCEL_SCALE
                val accelZ = az * ACCEL_SCALE

                val gyroX = wx * GYRO_SCALE
                val gyroY = wy * GYRO_SCALE
                val gyroZ = wz * GYRO_SCALE

                val rollAngle = roll * ANGLE_SCALE
                val pitchAngle = pitch * ANGLE_SCALE
                val yawAngle = yaw * ANGLE_SCALE

                // Calculate acceleration magnitude
                val accelMagnitude = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)

                // Adjusted acceleration (subtract gravity offset)
                val adjustedAccel = maxOf(0f, accelMagnitude - GRAVITY_OFFSET)

                val sensorData = SensorData(
                    accelX, accelY, accelZ,
                    gyroX, gyroY, gyroZ,
                    rollAngle, pitchAngle, yawAngle,
                    accelMagnitude, adjustedAccel
                )

                callback?.onSensorData(sensorData)

            } catch (e: Exception) {
                Log.e(TAG, "Error decoding packet: ${e.message}")
            }

            offset += 20
        }
    }
}
