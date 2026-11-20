# SoundFrame reference mapped to Pahlavi Studio

This document records the design and workflow requirements extracted from the supplied `فایل‌های html.zip` (12 HTML files). The HTML is a visual/interaction reference; it is not intended to be embedded as a WebView.

## Screen map

| Reference screen | Evidence in HTML | Native Android destination |
|---|---|---|
| Studio home | Welcome, new project, neon presets, recent projects, undo/redo/save | Project home and recent-project list |
| Equalizer design | Visual modes, lighting themes, HEX color, reset/apply style | Equalizer style and appearance editor |
| Timeline editor | Transport controls, visualizer region, split, zoom, mute source video, beat sync, track lock | Multi-track editor and preview |
| Export | 9:16, 16:9, 1:1, 4:5; 720p/1080p/2K/4K; 30/60 FPS | Export settings and render queue |

## Visual language

- Persian-first interface; the reference uses Vazirmatn with Plus Jakarta Sans as a Latin fallback.
- Dark, layered surfaces rather than large flat panels; compact toolbars and clearly separated editing regions.
- Neon spectrum accents in cyan/turquoise and magenta, with selectable palettes including amber, green and RGB.
- Motion previews are part of the editor, not static decorative illustrations.
- Keep controls concise. Avoid long explanatory paragraphs in the working UI.

## Required interaction inventory

### Studio
- New project, add/import media, audio library, recent projects.
- Undo, redo and save.
- Preset starting points (Trap, Lo-Fi, cinematic).

### Equalizer
- Distinct visual modes: bars, smooth line/wave, laser pulse and circular/radar.
- Palette selection: cyber neon, sunset/fire, matrix green and dynamic RGB.
- Custom HEX color, reset defaults, preview motion and apply style to the project.
- The final visualizer must be alpha-transparent and composited over the actual image/video. No opaque black backing rectangle.

### Timeline
- Play/pause, skip, seek and zoom.
- Split/cut, speed, beat synchronization and track locking.
- Independent source-video mute/volume control.
- Multiple media tracks and a visible visualizer region.

### Export
- Aspect ratios: 9:16, 16:9, 1:1 and 4:5.
- Resolution choices: 720p, 1080p, 2K and 4K.
- Frame rates: 30 and 60 FPS.
- Render to a real MP4 and save it to the device gallery.

## Implementation guardrails

1. Recreate screens with native Android controls/custom Views; do not ship the reference HTML as the application UI.
2. Keep the existing transparent overlay contract: only spectrum pixels are drawn; the underlying media remains visible.
3. Keep audio decoding and FFT processing bounded in memory. Do not retain decoded PCM for the full duration of long tracks.
4. Treat export completion as successful only after the MP4 is validated and copied to MediaStore.
5. A successful GitHub Actions build confirms compilation only; playback, compositing, timing and memory behavior still require device testing.

## Delivery sequence

1. Stabilize audio analysis and export memory use.
2. Build the SoundFrame-inspired native home and editor navigation.
3. Implement timeline editing, media ordering and source-audio controls.
4. Add draggable/resizable visualizer placement and persistent project settings.
5. Expand export controls and validate MP4 output on a physical Android device.
