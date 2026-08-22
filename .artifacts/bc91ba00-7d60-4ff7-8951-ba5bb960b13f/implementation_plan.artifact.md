# Fix TV App Black Screen & RTSP SETUP 400 Error

The TV app is experiencing a black screen on the emulator. Logcat analysis reveals an `androidx.media3.exoplayer.rtsp.RtspMediaSource$RtspPlaybackException: SETUP 400` error. This is a known issue with auto-negotiation of RTSP transport (UDP/TCP) in certain network environments, including emulators and wireless cameras.

## User Review Required

> [!IMPORTANT]
> The fix involves switching the TV app's RTSP transport to **Strict TCP**. This is consistent with the "Golden Build" standard already applied to the main app's preview but was previously set to auto-negotiation in the TV module.

## Proposed Changes

### [TV App Component]

#### [MODIFY] [TvDashboardScreen.kt](file:///C:/Users/iamre/AndroidStudioProjects/PBCam/tv/src/main/java/com/pbcam/tv/ui/TvDashboardScreen.kt)
- Update `RtspMediaSource.Factory()` in `VideoPlayerLayer` to use `.setForceUseRtpTcp(true)`. This ensures that the RTSP connection always uses TCP, avoiding the `SETUP 400` error caused by failed UDP negotiation.
- Add a diagnostic log to `VideoPlayerLayer` to print the `activeUrl` and `playbackStatus` to Logcat for easier debugging of future black screen issues.

## Verification Plan

### Automated Tests
- Build and run the `:tv` app on the emulator.
- Observe Logcat for the `activeUrl` being played.
- Verify that the `SETUP 400` error no longer appears.

### Manual Verification
- Deploy the updated TV app to the emulator.
- Ensure the main app is running on the phone and broadcasting status.
- Confirm the TV app transitions from "Connecting..." to "Playing" and shows the live feed or replay correctly.
