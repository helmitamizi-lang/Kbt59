# MEKXZ DRIVE X — Android Head Unit Conversion

Target branch: `mekxz-drive-x-headunit` (do not modify `main`).

## Target
- Android 13 / API 33 head unit, 1024 × 600 landscape, touch-first with optional hardware keys.
- Rebuild home screen using the user-provided MEKXZ DRIVE X reference image: rainy highway with black Vios KBT 59, large clock/date and weather on left, Kulim prayer glass card at right, 3×2 app grid lower left, wide media controls lower right, page dots bottom.
- Reference image is a visual specification, not a usable source asset unless added to the repository with permission.
- Preserve existing prayer/azan, UI click sounds, wallpaper selection/upload, screensaver, settings and compatible features.
- Replace TV-only Leanback app discovery with launchable Android applications, including ordinary phone apps; use PackageManager launcher intents and consider Android 11+ package visibility rules.
- Add an authenticated local Wi-Fi remote accessible from Safari for navigation, media controls, app launch and wallpaper upload. Bind to local network with explicit pairing and avoid unauthenticated commands.
- Connect playback to Android MediaSession; show genuine metadata/state; never hardcode sample song, prayer times, weather or battery voltage as live data.
- Allow local wallpaper changes without distorting the UI.

## Change controls and acceptance
1. Audit AndroidManifest, Gradle, Kotlin/Java, layouts, wallpaper service, app discovery, prayer engine, sound effects, existing HTTP server and CI before editing.
2. Keep main untouched. Record files changed and why.
3. Verify 1024×600 layout, system insets, launcher HOME intent, orientation and touch targets.
4. Verify app list includes non-TV apps and apps can launch.
5. Verify azan scheduling, sound effects and wallpaper switching.
6. Verify Safari remote pairing and LAN-only access.
7. Build debug APK with GitHub Actions and attach artifact; test on actual Android 13 head unit.

## Status
Branch and conversion specification created. Implementation, build, and device testing are NOT yet complete.
