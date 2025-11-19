# BeatBag

An Android app that turns your footbag (hacky sack) into a musical instrument using a WT9011DCL Bluetooth motion sensor. Each kick triggers a sound with intensity based on how hard you kick.

## Features

- **Real-time Kick Detection**: Uses advanced hysteresis algorithm to detect kicks with high accuracy
- **Sound Library**: Quick switching between different kick sounds
- **Adjustable Sensitivity**: Tune upper and lower thresholds to match your kicking style
- **Custom Sounds**: Add your own sound files (WAV, MP3, OGG)
- **Visual Feedback**: On-screen indicator flashes when a kick is detected

## Hardware Requirements

- Android device (API 26+, Android 8.0 Oreo or higher)
- WT9011DCL-BT50 9-axis Bluetooth motion sensor
- Footbag to attach the sensor to

## How It Works

The app connects to the WT9011DCL sensor via Bluetooth Low Energy (BLE) and receives acceleration data at 100Hz. The kick detection algorithm uses deadband hysteresis:

1. **Armed State**: System is ready to detect kicks
2. **Kick Detected**: When adjusted acceleration exceeds the upper threshold (default 1.5g), a sound plays
3. **Disarmed State**: System waits for acceleration to drop below lower threshold (default 0.1g)
4. **Re-armed**: Once acceleration drops below lower threshold, ready for next kick

This prevents multiple triggers from a single kick and ensures clean note separation.

## Building the App

### Prerequisites

- Android Studio (Arctic Fox or newer)
- Android SDK (API 26+)
- Gradle 8.0+

### Build Steps

1. Clone the repository:
```bash
cd ~/code
git clone <repository-url> beatbag
cd beatbag
```

2. Open the project in Android Studio

3. Add default kick sound files to `app/src/main/res/raw/` (optional):
   - kick1.wav
   - kick2.wav
   - kick3.wav

4. Uncomment sound loading code in `MainActivity.kt` if you added sound files

5. Build and run on your Android device (emulator won't work - needs BLE)

## Usage

1. **Grant Permissions**: Allow Bluetooth and Location permissions when prompted
2. **Scan for Sensor**: Tap "Scan for Sensor" button
3. **Connect**: Select your WT9011DCL sensor from the list
4. **Adjust Sensitivity**: Use the sliders to tune kick detection thresholds
5. **Select Sound**: Tap a sound in the library to activate it
6. **Kick Away**: Start kicking your footbag - each kick plays the selected sound!

## Adjusting Sensitivity

### Upper Threshold (0.0 - 4.0g)
- **Higher values**: Requires harder kicks to trigger
- **Lower values**: More sensitive, lighter kicks will trigger
- **Default**: 1.5g

### Lower Threshold (0.0 - 1.0g)
- **Higher values**: Faster re-arming between kicks
- **Lower values**: Prevents accidental double-triggers
- **Default**: 0.1g

## Adding Custom Sounds

### Method 1: Resource Files
1. Add audio files to `app/src/main/res/raw/`
2. In `MainActivity.kt`, add:
```kotlin
audioManager.addSoundFromResource("My Sound", R.raw.mysound)
```

### Method 2: External Files (TODO)
Future feature: File picker integration to add sounds from device storage

## Project Structure

```
beatbag/
├── app/src/main/java/com/beatbag/
│   ├── MainActivity.kt           # Main UI and app logic
│   ├── BleManager.kt            # Bluetooth communication
│   ├── KickDetector.kt          # Kick detection algorithm
│   └── BeatBagAudioManager.kt   # Sound library and playback
├── app/src/main/res/
│   ├── layout/                  # UI layouts
│   ├── values/                  # Strings, colors, themes
│   └── raw/                     # Default sound files
└── README.md
```

## Technical Details

### Sensor Communication
- **UUIDs**:
  - Notify: `0000ffe4-0000-1000-8000-00805f9a34fb`
  - Write: `0000ffe9-0000-1000-8000-00805f9a34fb`
- **Data Rate**: 100Hz
- **Packet Format**: 20 bytes (header + 9×int16 values)

### Data Scaling
- Acceleration: `value * (16.0 / 32768.0)` → ±16g range
- Gyroscope: `value * (2000.0 / 32768.0)` → ±2000°/s range
- Angles: `value * (180.0 / 32768.0)` → ±180° range

### Kick Detection
- **Adjusted Acceleration**: `max(0, accelMagnitude - 2.09)`
  - Subtracts baseline gravity offset
- **Magnitude**: `sqrt(x² + y² + z²)`

## Related Projects

- [midi-motion](../midi-motion) - Original Python version with MIDI output

## License

MIT License

## Credits

Built with Kotlin and Android SDK. Inspired by the original midi-motion Python project.
