# App discovery integration notes

This reference source is staged under `conversion-reference/` and is **not compiled** by the current Android project.

- Move the Kotlin file into the actual application source package after locating its existing app discovery code.
- Merge ordinary Android launcher activities (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) with any required TV-only activities (`CATEGORY_LEANBACK_LAUNCHER`) without duplicates.
- On Android 11+ add narrowly scoped `<queries>` intent declarations for launcher activities in AndroidManifest.xml; verify actual visibility on the head unit.
- Use package manager icons and real app labels rather than placeholder graphics.
- Some apps have no launcher activity and cannot be displayed as ordinary launchable apps.
- Do not replace the original app drawer until tested.
