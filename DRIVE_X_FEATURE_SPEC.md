# MEKXZ DRIVE X — Theme Studio Edition
Target: Android 13 (API 33), 1024×600 landscape, native Java Android. Working branch: `mekxz-drive-x`.

## Design contract
- Factory preset: pure black, white/silver liquid-glass panels, RS red/white GPS meter.
- Vios KBT 59 car artwork should be the bundled default **only when the user's licensed/reference asset is supplied and embedded**. Current build uses a user-selectable document image; it does **not** yet bundle the desired image.
- Fullscreen immersive, no quick-controls on the main screen, full-width dock, long JAKIM prayer card.
- Pixel-level acceptance requires screenshot comparisons on the actual 1024×600 head unit; passing Gradle is insufficient.
- Do not introduce Driving Focus Mode, Smart Theme Automation, or Backup/Restore/Theme Sharing.

## Delivery status and acceptance criteria
| Module | Current status | Acceptance criteria still required |
|---|---|---|
| Theme Studio | **Partial implementation** | Native preview, accent/glass/corner/glow/darkness and gauge presets are wired; require proper blur shaders, undo/redo, named preset management, and full control from Safari |
| Advanced Widget Editor | **Partial implementation** | Move, hide, and resize presets exist; require direct resize handles, snapping, layer order, and add/remove widget registry |
| iPhone Live Theme Editor | **Partial** | LAN remote exists; requires responsive live 1024×600 drag preview, secure pairing and all settings synchronization |
| Speedometer Skin Studio | **Partial** | Analog/digital and preset keys; require visually distinct AMG-inspired original skins, preview and accurate GPS tests |
| Car Visual Studio | **Partial** | Local image picker exists; require scale, crop, rotate, reflection, and bundled default photo |
| Multiple Homescreen Pages | **Not implemented** | Independent page layouts, swipe animation, edit page order, media/lyrics and trip pages |
| Premium Voice Greeting Studio | **Partial** | Android TTS on boot; require audio-file upload, natural recorded greeting, sound packs and configurable trigger |
| Cinematic Lyrics | **Partial** | Local .LRC import and timestamp display; require full-screen lyrics page, album blur, scroll and sync offsets |
| Custom Radio Dashboard | **Partial** | MediaSession support; require independently licensed streaming station presets, real metadata and artwork, OEM radio bridge only if accessible |
| Premium UI Animation Engine | **Not implemented** | Frame-stable transitions, animation toggles, effects and GPU budget tests |
| Screensaver Studio | **Partial** | Digital clock and dim mode; require analog, album, car, floating clock and real brightness options |
| Advanced Weather | **Partial** | Open-Meteo current conditions; require hourly forecast, precipitation, humidity, sunrise/sunset and separate verified air quality provider |
| Smart Prayer Center | **Partial** | Existing JAKIM/azan component and KDH05 fallback; require countdown, sound selection, notification reliability and media audio-focus handling |
| Layout Recovery | **Not implemented** | Explicit reset of offscreen/invalid widget coordinates without erasing all preferences |

## Architecture
- `DriveXActivity`: main native cockpit; coordinates, media, GPS, prayer, weather, settings and fullscreen.
- `DriveXThemeStudioActivity`: native visual appearance editor.
- `DriveXRemoteServer`: opt-in local HTTP controls; security and pairing hardening still required before exposing to untrusted Wi-Fi.
- Reused Glass TV services: `PrayerTimes`, `Azan`, `HomeWeather`, `NowPlayingManager`, `SoundEffects`.
- Planned: `ThemeRepository`, `WidgetLayoutEngine`, `CarVisualRenderer`, `ScreenSaverController`, `RadioRepository`, `LyricRepository`, `DriveXPageManager`, `GreetingAudioController`.
- Never fabricate GPS speed, prayer data, radio track metadata, or weather.

## QA gates
1. Gradle `assembleDebug` success.
2. Install APK on actual Android 13 head unit; launch as Home and verify recovery to old launcher.
3. Compare 1024×600 screenshot against approved design; check all controls are on-screen.
4. Confirm no freeze/ANR with media playing while leaving Home; test radio OEM and local player separately.
5. Test offline Kulim KDH05 fallback, GPS changes, JAKIM errors, azan with screen off and notification permissions.
6. Safari same-Wi-Fi test, pairing protection, reconnect, widget changes and network isolation.
7. Test with GPS unavailable and media metadata absent; never show fake live data.
8. Confirm update installation preserves existing app preferences.

This is a living implementation checklist, **not a claim that all features are complete**.
