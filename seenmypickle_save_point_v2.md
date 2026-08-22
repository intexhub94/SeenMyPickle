# SeenMyPickle: Golden Build Save Point V2 (Aug 2026)

This document anchors the current state and the next high-impact objectives to ensure 100% continuity.

## 1. Current Stable Baseline (REACHED)
- **Brand**: **SeenMyPickle** rebrand complete across all assets and code.
- **Dual-Stream RTSP**: **Sub-Stream** preview and **Automated One-Tap Setup** (Dahua/Hikvision auto-fill) fully implemented and verified.
- **Header Visibility**: Aggressive drop shadows and contrast-hardened progress bars implemented.
- **Device Stability**: **6Mbps bitrate**, **CFR 30fps**, and **Sleep-Prevention** rules are active.

## 2. Pending Mission: Turbo-Processing & Remote Control
- **Turbo Watermarking**:
    - **Dynamic CPU Detection**: Function to auto-detect core count (Quad/Octa) and pass to FFmpeg `-threads`.
    - **Logo Pre-scaling**: Move logo scaling outside the frame-loop to save massive CPU cycles.
    - **Hardware Tuning**: 8Mbps MediaCodec boost with `zerolatency` tuning.
- **Notification Shutdown**:
    - Add a permanent "SHUTDOWN" action button to the Foreground Notification bar.
    - Handle `ACTION_EXIT_APP` to kill the process for absolute user control.

## 3. Context Confirmation
- **Files Audited**: `RecordingService.kt`, `ConvertWorker.kt`, `UploadWorker.kt`, `SettingsStore.kt`.
- **Status**: **YES**, our context is more than enough to proceed with these low-level hardware optimizations.

---
**Continuity Note**: To resume from this point, tell the AI: *"Load SeenMyPickle Save Point V2 and implement the Turbo Watermarking and Notification Shutdown features."*
