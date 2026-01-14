# Photo Booth Android App

A simple Android photo booth application built with Kotlin.

## Project Setup

This is a basic "Hello World" Android application that will be expanded into a full photo booth app.

### Prerequisites

1. **Android Studio** - Download and install from [https://developer.android.com/studio](https://developer.android.com/studio)
2. **Java Development Kit (JDK)** - Version 8 or higher
3. **Android SDK** - Installed via Android Studio

### Project Structure

```
fotomaton2/
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/photobooth/app/
│   │       │   └── MainActivity.kt
│   │       └── res/
│   │           ├── layout/
│   │           │   └── activity_main.xml
│   │           └── values/
│   │               ├── colors.xml
│   │               ├── strings.xml
│   │               └── themes.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## How to Run on Your Mobile Phone

### Method 1: USB Debugging (Recommended for Development)

1. **Enable Developer Options on your phone:**
   - Go to Settings > About Phone
   - Tap "Build Number" 7 times
   - Developer Options will be enabled

2. **Enable USB Debugging:**
   - Go to Settings > Developer Options
   - Enable "USB Debugging"

3. **Connect your phone:**
   - Connect your Android phone to your computer via USB
   - Allow USB debugging when prompted on your phone

4. **Open the project in Android Studio:**
   - Launch Android Studio
   - Click "Open" and select the `fotomaton2` folder
   - Wait for Gradle sync to complete

5. **Run the app:**
   - Make sure your device appears in the device dropdown (top toolbar)
   - Click the green "Run" button (▶️) or press Shift+F10
   - The app will install and launch on your phone

### Method 2: Build and Install APK Manually

1. **Build the APK in Android Studio:**
   - Open Terminal in Android Studio
   - Run: `./gradlew assembleDebug` (on Windows: `gradlew.bat assembleDebug`)
   - APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

2. **Transfer and Install:**
   - Copy the APK to your phone
   - Enable "Install from Unknown Sources" in Settings
   - Open the APK file on your phone to install

### Troubleshooting

**Device not detected:**
- Install the correct USB drivers for your phone
- Try a different USB cable
- Make sure USB debugging is enabled

**Gradle sync failed:**
- Check your internet connection
- Android Studio will download required dependencies
- Wait for the sync to complete

**Build errors:**
- Make sure you have Android SDK Platform 34 installed
- Go to Tools > SDK Manager in Android Studio

## Current Features

### Core Functionality
- ✅ **Event Management**: Create and manage photo booth events
- ✅ **Photo Capture**: Take photos with filters and effects
- ✅ **Video Recording**: Record videos with slow motion and boomerang effects
- ✅ **Photo Booth Mode**: Automatic 4-photo collage
- ✅ **Gallery**: View and manage photos by event
- ✅ **Theme Support**: Dark/Light mode with dynamic colors

### Photo Effects
- ✅ **Filters**: Black & white, vintage, sepia, etc.
- ✅ **Decorative Frames**: Real-time preview with themed frames
  - Organized by theme: Summer, Christmas, Birthday, Winter, Communion, Wedding, Other
  - See [INSTRUCCIONES_MARCOS.md](INSTRUCCIONES_MARCOS.md) for details
- ✅ **Background Removal**: ML Kit powered background segmentation
- ✅ **Virtual Backgrounds**: Replace backgrounds with custom images

### Video Effects
- ✅ **Slow Motion**: 0.5x speed with FFmpeg processing
- ✅ **Boomerang**: Dynamic speed variations
- ✅ **Quality Selection**: HD and 4K support

### Frame System Features
🎨 **New Frame Preview System**:
- Real-time camera preview with frame overlay
- Theme-based organization (7 categories)
- Dynamic asset scanning (no code changes needed)
- Horizontal scrolling UI for easy selection
- Visual feedback with checkmarks

To add frames:
1. Create PNG files with transparency
2. Copy to `app/src/main/assets/marcos/[theme]/`
3. App automatically detects new frames
4. See full instructions in [INSTRUCCIONES_MARCOS.md](INSTRUCCIONES_MARCOS.md)

## Next Steps

Planned features:
- [ ] Background preview system (similar to frames)
- [ ] Print functionality
- [ ] QR code sharing
- [ ] Cloud backup
- [ ] Social media sharing

## Technical Details

- **Language:** Kotlin
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **Build System:** Gradle with Kotlin DSL
- **Architecture:** View Binding, Activity-based

### Key Dependencies
- **CameraX**: Modern camera API
- **ML Kit Selfie Segmentation**: Background removal
- **FFmpeg Kit**: Video processing (slow motion)
- **Material Design 3**: UI components
- **ViewBinding**: Type-safe view access

### Frame System Architecture
```kotlin
PhotoConfigActivity
    └─> [Select Frame Button]
         └─> FramePreviewActivity
              ├─> CameraX Preview
              ├─> ThemeAdapter (RecyclerView)
              ├─> FrameAdapter (RecyclerView)
              └─> Real-time overlay preview
                   └─> Returns frame path
                        └─> CameraActivity applies frame
```

Asset Structure:
```
assets/
  ├── marcos/[theme]/frame.png  (decorative frames)
  └── fondos/[theme]/bg.jpg     (virtual backgrounds)
```

## License

This project is for educational purposes.
