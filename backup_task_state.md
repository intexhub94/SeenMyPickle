# SeeMyPickle Task Backup - Session 2026-07-23

## Current Status
We have completed several core features (Pause Dimming, Standby Overlay, Optimized Notifications). We are now moving into a multi-part bug fix and UI refinement phase.

## Approved Implementation Plan
The following plan has been reviewed and is ready for execution:

1.  **Back Button & Unsaved Changes**: 
    - Implement `BackHandler` in `AdminPanel`.
    - Detect if `localRtsp`, `localCourt`, or `backgroundUrl` differ from stored settings.
    - Prompt "Save or Discard?" dialog on back gesture.

2.  **PIN Security (3-Strike Rule)**: 
    - Add `passcodeAttempts` and `lockoutEndTime` to `DashboardUiState`.
    - Lock Admin button for 60s after 3 failed attempts.
    - Strictly enforce 4-digit max length for all PIN inputs.

3.  **Simplified Shutdown**: 
    - Remove PIN requirement for the Power (Exit) button.
    - Replace with a simple "Yes/No" confirmation dialog.

4.  **Internal Camera Orientation**: 
    - Fix inverted video in portrait mode.
    - Handle 90/270 degree rotation mapping in `RecordingService.kt`.

5.  **Email Auto-Clear**: 
    - Automatically empty the player email field after "Sent to user" notification completes.

6.  **Residual Notification Fix**: 
    - Clear old status messages on ViewModel init and prevent new ones during shutdown.

## File References
- [DashboardScreen.kt](file:///C:/Users/iamre/AndroidStudioProjects/PBCam/app/src/main/java/com/pbcam/app/ui/DashboardScreen.kt)
- [DashboardViewModel.kt](file:///C:/Users/iamre/AndroidStudioProjects/PBCam/app/src/main/java/com/pbcam/app/ui/viewmodel/DashboardViewModel.kt)
- [RecordingService.kt](file:///C:/Users/iamre/AndroidStudioProjects/PBCam/app/src/main/java/com/pbcam/app/service/RecordingService.kt)
- [SetupWizardScreen.kt](file:///C:/Users/iamre/AndroidStudioProjects/PBCam/app/src/main/java/com/pbcam/app/ui/SetupWizardScreen.kt)

## Next Steps for New Session
1.  Verify if the session budget is low (check prompt size).
2.  If starting fresh, use `adb shell pm clear com.pbcam.app` to test the Setup Wizard.
3.  Apply the changes to [DashboardScreen.kt] first, as it contains the bulk of the UI refinements.
