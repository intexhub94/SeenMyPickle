# New Feature: Background Video Player Completion

## Problem
The `BackgroundVideoPlayer` exists but isn't integrated to automatically play when the primary camera source is paused or inactive. It currently only shows when `previewState` is `IDLE`, but doesn't specifically react to recording pauses.

## Proposed Changes

### [DashboardScreen.kt](file:///C:/Users/iamre/AndroidStudioProjects/PBCam/app/src/main/java/com/pbcam/app/ui/DashboardScreen.kt)
- Modify the background visibility logic.
- The background video should play if:
    1. The `previewState` is `IDLE` (no active live view).
    2. OR the `recordingState` is `PAUSED` (any pause reason), even if the preview is technically active, to provide a "professional standby" look.

```kotlin
// In DashboardScreen.kt
val showBackground = uiState.previewState == PreviewState.IDLE || 
                     uiState.recordingState in listOf(
                         RecordingState.PAUSED, 
                         RecordingState.PAUSED_LOW_STORAGE, 
                         RecordingState.PAUSED_OVERHEATING
                     )

if (showBackground) {
    BackgroundVideoPlayer(
        modifier = Modifier.fillMaxSize(),
        url = uiState.backgroundVideoUrl
    )
}
```

## Verification Plan
1. Configure a YouTube or MP4 background URL in Admin Panel.
2. Ensure camera preview is stopped -> Background video should play.
3. Start recording -> Background video should disappear, replaced by camera.
4. Tap "Pause" -> Background video should reappear over the camera view.
5. Tap "Resume" -> Camera view should return.
