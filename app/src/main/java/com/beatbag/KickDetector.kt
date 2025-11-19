package com.beatbag

import android.util.Log

/**
 * Detects kicks using deadband hysteresis algorithm
 * Ported from motion.py handle_note_triggering() function
 */
class KickDetector {

    companion object {
        private const val TAG = "KickDetector"
        const val DEFAULT_UPPER_THRESHOLD = 1.5f  // 1.5g
        const val DEFAULT_LOWER_THRESHOLD = 0.1f  // 0.1g
    }

    interface KickListener {
        fun onKickDetected(intensity: Float)
    }

    private var listener: KickListener? = null
    private var isArmed = true  // Ready to detect kicks

    // Configurable thresholds
    var upperThreshold = DEFAULT_UPPER_THRESHOLD
        set(value) {
            field = value.coerceAtLeast(0.1f)
            Log.d(TAG, "Upper threshold set to: $field")
        }

    var lowerThreshold = DEFAULT_LOWER_THRESHOLD
        set(value) {
            field = value.coerceAtLeast(0.01f)
            Log.d(TAG, "Lower threshold set to: $field")
        }

    fun setListener(listener: KickListener) {
        this.listener = listener
    }

    /**
     * Process sensor data and detect kicks using deadband hysteresis
     * @param adjustedAccel Adjusted acceleration magnitude (gravity-compensated)
     */
    fun processSensorData(adjustedAccel: Float) {
        when {
            // Trigger kick when crossing upper threshold while armed
            adjustedAccel > upperThreshold && isArmed -> {
                // Calculate kick intensity (normalized 0-1)
                val intensity = (adjustedAccel / upperThreshold).coerceAtMost(2.0f)

                Log.d(TAG, "Kick detected! Intensity: $intensity, Accel: $adjustedAccel")

                listener?.onKickDetected(intensity)

                // Disarm until signal drops below lower threshold
                isArmed = false
            }

            // Re-arm when signal drops below lower threshold
            adjustedAccel < lowerThreshold && !isArmed -> {
                Log.d(TAG, "Re-armed for next kick")
                isArmed = true
            }
        }
    }

    /**
     * Reset the detector state
     */
    fun reset() {
        isArmed = true
        Log.d(TAG, "Detector reset")
    }

    /**
     * Get current arm state (for UI feedback)
     */
    fun isArmed(): Boolean = isArmed
}
