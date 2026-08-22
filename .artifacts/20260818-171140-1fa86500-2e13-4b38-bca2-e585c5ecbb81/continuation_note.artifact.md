# Continuation Note: TV Supplementary App Integration

## 📍 Current State
The `:tv` module has been fully implemented and configured with a unique App ID (`com.pbcam.tv.app`) to resolve deployment conflicts. The main app (`:app`) is successfully broadcasting its live status and replay URLs to Firebase.

## ✅ Accomplished
1.  **TV Logic**: Created `TvDashboardViewModel` and `TvDashboardScreen` with mode-switching and hardened RTSP playback.
2.  **Diagnostics**: Added an on-screen status overlay to the TV app for troubleshooting.
3.  **Broadcasting**: Enhanced `DashboardViewModel` and `UploadWorker` in the main app to push status updates.
4.  **Documentation**: Updated `code_bible.md` and `code_map.md` with the new TV architecture.

## 🚧 Next Steps
1.  **Firebase Update**: Wait for the user to provide the updated `google-services.json` containing the client for `com.pbcam.tv.app`.
2.  **Sync & Build**: Copy the new JSON to `tv/google-services.json` and run a clean build.
3.  **Deployment**: Uninstall any old versions of "PickleView" from the TV and perform a fresh install of the `:tv` module.
4.  **Testing**: Verify the "STATUS" message at the bottom-left of the TV and ensure the live feed appears when a match is started on the tablet.

## 🔑 Key IDs
- **New TV App ID**: `com.pbcam.tv`
- **Main Tablet App ID**: `com.pbcam.app`
- **Firebase Node**: `live_status/{DEVICE_ID}`
