# Implementation Plan: TV App HDR Display Mode Support & Admin Toggle

## Goal Description
Implement configurable HDR (High Dynamic Range) display support in the TV app (`:tv` module):
1. Detect display HDR capabilities (`hdrCapabilities.supportedHdrTypes`).
2. Add a persistent **HDR Mode Toggle** in the **TV Admin Panel** allowing court managers to enable or disable forced HDR mode.
3. Automatically apply `window.colorMode = ActivityInfo.COLOR_MODE_HDR` when enabled (and `COLOR_MODE_DEFAULT` when disabled) on API level 26+ devices.

---

## User Review Required

> [!IMPORTANT]
> The TV Admin Panel will include a new setting toggle: **HDR MODE (HIGH DYNAMIC RANGE)**. When enabled on HDR-supported TV displays, the app will switch the TV window color mode to `COLOR_MODE_HDR` for 10-bit wide color gamut output.

---

## Proposed Changes

### Manifest & Core TV Layer

#### [MODIFY] [AndroidManifest.xml](file:///home/intexhub/StudioProjects/SeenMyPickle/tv/src/main/AndroidManifest.xml)
- Add `android:colorMode="hdr"` attribute to `MainActivity`.

#### [MODIFY] [TvDashboardViewModel.kt](file:///home/intexhub/StudioProjects/SeenMyPickle/tv/src/main/java/com/pbcam/tv/ui/TvDashboardViewModel.kt)
- Add `useHdrMode: Boolean` and `isHdrSupported: Boolean` to `TvUiState`.
- Implement `checkHdrSupport(context)` during ViewModel initialization to query `display.hdrCapabilities`.
- Implement `toggleHdrMode()` persisting preference in `tv_prefs` (`use_hdr_mode`).

#### [MODIFY] [TvDashboardScreen.kt](file:///home/intexhub/StudioProjects/SeenMyPickle/tv/src/main/java/com/pbcam/tv/ui/TvDashboardScreen.kt)
- Add `LaunchedEffect(uiState.useHdrMode)` in `TvDashboardScreen` to apply `window.colorMode = ActivityInfo.COLOR_MODE_HDR` (or `COLOR_MODE_DEFAULT`).
- Add an interactive **HDR DISPLAY MODE** toggle card in `AdminPanelDialog`.
- Add a diagnostic row: `TV Display HDR: SUPPORTED / NOT SUPPORTED`.

---

## Impact Analysis & Golden Build Stability
- **TV Module Only**: Changes are isolated to `:tv` module.
- **Graceful Fallback**: On non-HDR TVs or API < 26, `useHdrMode` safely defaults to disabled without any visual or execution side effects.

---

## Verification Plan

### Automated Build Verification
- Execute `gradle_build(":tv:assembleDebug")` to confirm clean compilation.

### Manual Verification Steps
1. Launch TV App and open the **Admin Panel**.
2. Verify the **TV Display HDR** status row in Technical Diagnostics.
3. Toggle **HDR MODE (HIGH DYNAMIC RANGE)** on or off.
4. Verify preference is saved and applied cleanly.
