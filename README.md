# Iran Music Studio — v0.1.0

Android-native starter for a Persian-language cinematic music visualizer.

## Included in v0.1.0
- Dark navy and gold Android interface.
- Select audio files through Android's system picker (MP3, WAV, M4A where supported).
- Audio playback with AndroidX Media3 ExoPlayer.
- Select an image or video as a background asset.
- Live FFT spectrum preview using Android's Visualizer API.
- Green / white / red spectrum bars.
- GitHub Actions workflow that builds a debug APK and uploads it as an artifact.

## Current limitations
This is the first functional foundation, not the finished video editor:
- The selected background is currently only selected and retained as a URI; it is not yet composited into the preview.
- MP4 rendering/export is not implemented yet.
- The current visualizer is a live preview only; the next increment will add multiple styles and synchronized rendering.
- The Visualizer API may be unavailable on some devices or audio routes. Playback should still work without it.

## Build
Requires JDK 17, Android SDK 35, and Gradle 8.9:
```bash
gradle assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions
A push to `main` or manual workflow dispatch starts the Android Debug APK workflow. Download the APK from the workflow run's Artifacts section.

## Privacy and media rights
- No server, API key, or upload service is used.
- Audio and background files are selected locally on the device.
- The app requests RECORD_AUDIO permission only to access Android's playback Visualizer session; it does not record or upload microphone audio.
- Add only portraits, photographs, music, and stock footage that you have permission to use.
