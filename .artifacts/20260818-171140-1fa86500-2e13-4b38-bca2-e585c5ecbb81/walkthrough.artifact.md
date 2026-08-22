# Walkthrough - Android TV Supplementary App (PickleView)

This document summarizes the implementation of the **PickleView** Android TV application and the corresponding enhancements to the **PBCam** main application.

## 🚀 Accomplishments

### 1. Multi-Module Architecture
- Created a new `:tv` module in the existing project.
- Configured the TV module with **Leanback** support and **Media3** for low-latency RTSP playback.
- Shared the core `:app` logic to reuse data models and security utilities.

### 2. Main App: Cloud Status Publisher
- Enhanced `DashboardViewModel.kt` to push real-time status updates (Recording/Paused/Duration) to **Firebase Realtime Database**.
- Throttled duration updates to every 5 seconds to minimize battery and network overhead while maintaining visibility.
- Implemented `updateCloudStatusWithReplay` in `UploadWorker.kt` to share the final Google Drive URL for instant replay.

### 3. TV App: Mirror Dashboard
- Developed a high-contrast, TV-optimized dashboard in `TvDashboardScreen.kt`.
- **Mode Switching Logic**:
    - **Live Mode**: Automatically starts the RTSP feed when the tablet begins recording.
    - **Replay Mode**: Detects the end of a match and automatically plays the recorded session from Google Drive.
    - **Handoff**: Seamlessly cuts back to live video as soon as a new match is initiated.
- **Pulsing Status Banner**: Implemented the "Golden Build" status banner (MATCH LIVE, INSTANT REPLAY, MATCH PAUSED).

### 4. Zero-Config Pairing
- Implemented a simple **Device ID Pairing** system.
- Users only need to enter the Tablet's unique ID once on the TV to establish a permanent cloud link.

### 5. Tablet Offline Detection
- Implemented a real-time presence heartbeat using Firebase `.info/connected`.
- The TV app now displays a high-visibility **"TABLET OFFLINE"** overlay if the phone app is closed or loses internet.

### 6. Continuous Monitor (Stream 2)
- Optimized the TV player to use the camera's **Sub Stream (stream2)**.
- This provides a low-latency, continuous 24/7 feed that remains active even when the tablet is not recording.
- Seamlessly transitions status overlays (LIVE FEED, MATCH LIVE) while the video stream remains uninterrupted.

---

## 🛡️ Verification Summary

### Manual Verification Steps
1.  **Pairing**: Open the TV app and enter the Tablet's Device ID. Verify the "LIVE FEED" status appears.
2.  **Live Mirroring**: Start a recording on the Tablet. Verify the TV switches to "MATCH LIVE" and displays the RTSP stream.
3.  **Duration Sync**: Watch the TV timer; verify it increments in sync with the Tablet (within 5s intervals).
4.  **Auto-Replay**: Stop the recording and wait for the upload to finish. Verify the TV automatically switches to "INSTANT REPLAY" and loops the match.
5.  **Handoff Test**: Start a new match while the replay is running. Verify the TV cuts back to the live feed instantly.

### Technical Guardrails
- **Impact Analysis**: The main app's recording service remains isolated. Cloud sync failures are caught silently and do not interrupt recording.
- **Resource Management**: TV app uses a single `ExoPlayer` instance that is lifecycle-aware and correctly released.
