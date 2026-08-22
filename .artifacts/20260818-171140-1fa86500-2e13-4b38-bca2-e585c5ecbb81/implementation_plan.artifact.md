# Implementation Plan - Tablet Offline Detection & Continuous Monitoring

This plan outlines the implementation of real-time "Tablet Offline" detection for the TV app and the transition to continuous monitoring using the camera's Sub Stream (stream2).

## 🛡️ Impact Analysis (Golden Build Stability)
- **Zero-Recording Interference**: Offline detection is handled via Firebase's native `.info/connected` mechanism, which has zero impact on the recording SOC.
- **Resource Optimization**: Using `stream2` (Sub Stream) for continuous TV monitoring reduces Wi-Fi bandwidth and TV CPU usage compared to the Main Stream.

## 🔍 Context Audit
- **Context Sufficiency**: **YES**. I have full access to `DashboardViewModel.kt` and the `:tv` module.
- **Cross-Component Regression**: Verified that adding a `lastSeen` heartbeat will not interfere with the `RecordingService`.

---

## Proposed Changes

### [Component] Main App Enhancement (`:app`)

#### [DashboardViewModel.kt](file:///C:/Users/iamre/AndroidStudioProjects/PBCam/app/src/main/java/com/pbcam/app/ui/viewmodel/DashboardViewModel.kt)
- **Presence Heartbeat**: Implement a Firebase `.info/connected` listener.
- **Auto-Cleanup**: Use `onDisconnect().setValue(false)` to automatically mark the tablet as offline in the cloud if the app is closed or loses internet.
- **Continuous URL**: Ensure `rtspSubUrl` (stream2) is always broadcasted to the cloud during IDLE and RECORDING states.

---

### [Component] TV App Enhancements (`:tv`)

#### [TvDashboardViewModel.kt](file:///C:/Users/iamre/AndroidStudioProjects/PBCam/tv/src/main/java/com/pbcam/tv/ui/TvDashboardViewModel.kt)
- **Presence Listener**: Monitor the `isOnline` flag for the paired Device ID.
- **Sub-Stream Priority**: Update `TvUiState` to include `rtspSubUrl` and prioritize it for continuous viewing.

#### [TvDashboardScreen.kt](file:///C:/Users/iamre/AndroidStudioProjects/PBCam/tv/src/main/java/com/pbcam/tv/ui/TvDashboardScreen.kt)
- **Offline Overlay**: Implement a high-visibility **"TABLET OFFLINE"** popup (similar to the Connection Lost popup) that appears if `isOnline` is false.
- **Video Logic**: Update `VideoPlayerLayer` to utilize `rtspSubUrl` for continuous feed.

---

## Verification Plan

### Manual Verification
1. **Offline Alert**: Close the phone app entirely. Verify the TV shows **"TABLET OFFLINE"** within 5-10 seconds.
2. **Re-connection**: Open the phone app. Verify the TV alert vanishes and the live feed resumes instantly.
3. **Sub-Stream Verification**: Inspect logs to confirm the TV is connecting to the `/stream2` URL.
4. **Continuous Feed**: Verify the TV shows the camera feed even when the phone is just sitting on the dashboard (not recording).

### Build Verification
- Run `app:assembleDebug` and `tv:assembleDebug`.
