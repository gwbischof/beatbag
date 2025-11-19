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

**Option A: Android Studio (Easiest)**
- Android Studio (Arctic Fox or newer) - includes Android SDK and Gradle

**Option B: Command Line**
- Android SDK Command Line Tools
- Gradle 8.0+ (or use included Gradle wrapper)
- Java JDK 17+

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

5. **Prepare your Android phone**:
   - Enable Developer Mode:
     - Go to Settings → About Phone
     - Tap "Build Number" 7 times until it says "You are now a developer"
   - Enable USB Debugging:
     - Go to Settings → System → Developer Options
     - Enable "USB Debugging"
   - Connect your phone to computer via USB

6. **Run the app**:
   - In Android Studio, wait for Gradle sync to complete
   - Your phone should appear in the device dropdown at the top
   - If prompted on your phone, accept "Allow USB Debugging"
   - Click the green "Run" button (▶) in Android Studio
   - The app will install and launch on your phone

Note: Emulator won't work - BLE requires real hardware

### Alternative: Build from Command Line (No Android Studio)

If you don't want to use Android Studio, you can build and install from the command line:

1. **Install Android SDK Command Line Tools**:
   - Download from: https://developer.android.com/studio#command-tools
   - Extract and set `ANDROID_HOME` environment variable:
     ```bash
     export ANDROID_HOME=/path/to/android-sdk
     export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
     ```

2. **Install required SDK components**:
   ```bash
   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```

3. **Prepare your phone** (same as above):
   - Enable Developer Mode and USB Debugging
   - Connect via USB

4. **Initialize Gradle wrapper** (first time only):
   ```bash
   cd ~/code/beatbag
   gradle wrapper  # Downloads Gradle wrapper jar
   ```

5. **Build and install**:
   ```bash
   ./gradlew installDebug
   ```
   The first run will download Gradle automatically.

6. **Launch the app** manually on your phone (look for "BeatBag" in app drawer)

The app will be installed on your connected Android device!

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

## Distributing the App

### Building APK Locally

To build an APK you can share with others:

```bash
cd ~/code/beatbag
./gradlew assembleRelease
```

The APK will be at: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Automated Builds with GitHub Actions

The repo includes a GitHub Actions workflow that automatically builds the APK when you create a release:

1. **Push your code to GitHub**:
   ```bash
   git push origin main
   ```

2. **Create a release**:
   ```bash
   # Create and push a tag
   git tag v1.0.0
   git push origin v1.0.0
   ```

3. **Create GitHub Release**:
   - Go to your repo on GitHub
   - Click "Releases" → "Create a new release"
   - Select the tag you just pushed (v1.0.0)
   - Add release notes
   - Click "Publish release"

4. **Download the APK**:
   - GitHub Actions will automatically build the APK
   - Once complete, the APK will be attached to the release
   - Anyone can download `BeatBag-v1.0.0.apk` from the release page

### Installing on Other Phones

Share the APK file with others. They'll need to:
1. Download the APK to their Android phone
2. Enable "Install from Unknown Sources" in Settings → Security
3. Open the APK file and tap "Install"

## Related Projects

- [midi-motion](../midi-motion) - Original Python version with MIDI output

## License

MIT License

## Credits

Built with Kotlin and Android SDK. Inspired by the original midi-motion Python project.
