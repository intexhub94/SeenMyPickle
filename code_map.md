# SeenMyPickle: System Architecture & Code Map (2026 Golden Build)

This document serves as the master logical map for the SeenMyPickle Android project. It outlines the relationships between components, data flows, and the "Code Bible" implementation details.

---

## 🏗️ 1. High-Level Architecture
- **Pattern**: MVVM (Model-View-ViewModel) with Clean Architecture principles.
- **UI Framework**: 100% Jetpack Compose with Material 3.
- **Concurrency**: Kotlin Coroutines & Flows for reactive state.
- **Background Tasks**: WorkManager for reliable offline-capable video processing.
- **Database**: Room for local recording persistence and pipeline tracking.

---

## 🧩 2. Core Component Map

### 📱 UI Layer (Package: `com.pbcam.app.ui`)
- **`SplashActivity.kt`**:
    - **Branding Bridge**: 100% reliable 2s branding duration.
    - **Diagnostics**: Implements real-time System Readiness Check (Network, Camera, Google, Storage) with high-contrast bottom-center overlay (`bottom = 20.dp`).
- **`MainActivity.kt`**: 
    - Entry point. 
    - Logic: Permission gating, Dynamic Orientation (Portrait for Setup on mobile, Landscape for Dashboard), Edge-to-edge implementation.
    - **Hardening**: Dynamically enforces `FLAG_KEEP_SCREEN_ON` and `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to ensure hardware/service continuity.
- **`DashboardViewModel.kt`**:
    - **The "Brain"**: Centralized state (`DashboardUiState`).
    - Logic: Real-time licensing (Firebase), dual-stream state management, descriptive status engine, and admin authorization.
    - **Licensing Resilience**: Implements **Local Key Primary Truth** logic. Only invalidates license if cloud explicitly revokes or expires.
    - **Optimization (Round 25)**: Implements **Timer Decoupling** and **On-Demand Frame Capture** using standalone `StateFlow` and `SharedFlow` to minimize UI recomposition overhead.
    - **Cloud Continuity**: Implements **Atomic Status Sync** via `updateChildren` and a dedicated **10s Presence Heartbeat** to ensure 100% reliable TV offline detection. All UI and URL state changes are synchronized in a single transaction to prevent TV player flickering.
    - **Google Auth Recovery**: Implements reactive `isAuthenticated` tracking within the standard `refreshSettings` cycle.
    - **Replay Handover**: Broadcasts `lastReplaySessionId` and manages the lifecycle of the `LocalReplayServer` to trigger automated TV match review.
    - **Configuration Logic**: Implements `isConfigReady` calculation (Gate for READY/CONFIG REQUIRED status). Dashboard automatically enables START MATCH if a valid camera source is selected.
    - **Observability**: Implements the **5-second Auto-Dismiss** rule for background task progress.
    - **Security**: Implements `verifyPasscode` with emergency master override (**2026**) and detailed recovery logging.
    - **Update Engine**: Manages silent and manual check cycles using anonymous Drive lookups.
- **`DashboardScreen.kt`**:
    - **The "Face"**: Implements the multi-layer Z-Index strategy.
    - Features: 
        - **Dynamic Orientation**: Portrait for Setup on mobile, Landscape for Dashboard on all devices.
        - **Admin Authorization**: Implements `PasscodeEntryDialog` with a custom **`AdminNumericKeypad`** and side-by-side landscape layout to eliminate system keyboard conflicts.
        - **Perfectly Centered Branding**: App icon and splash logo centered using gravity-locked layer-lists to prevent hardware-specific scaling artifacts.
        - **High-Contrast Header**: Branding area and progress information wrapped in a harmonized pill surface (`0.4f` black, `8.dp` shadow, `16.dp` corners).
        - **Pairing ID Visibility**: Displays the **TV PAIRING ID** in the top-left pill for frictionless court-side setup.
        - **Multi-Player Support**: Integrated `AssistChip` UI allowing up to 5 player emails per session with smart input-to-chip handoff.
        - **Broadcast Mode**: Centralized status banner (Internal/USB) and unified center card (RTSP).
        - **Immersive Continuity**: Frozen Frame bitmap capture (720p). **Anti-Ghosting**: Snapshot loop is automatically suspended when keyboard is visible to prevent background burn-in.
        - **Streamlined Controls**: The recording card focuses exclusively on email entry (Title: "Match Recording" removed for clarity).
        - **Adaptive Layout**: Corner-anchored controls for all devices.
        - **Auto-Preview**: Automatically triggers monitoring after waiver acknowledgement.
- **`SetupWizardScreen.kt`**:
    - **Onboarding**: 5-step automated configuration including "One-Tap" RTSP setup, mandatory Requirements/Privacy disclaimer, and **Manual Brand Guide** (Dahua/Hikvision/Tapo).
    - **Data Flow**: Communicates **directly** with `DashboardViewModel` during setup completion to ensure 100% data integrity and avoid stale closure issues.
    - **UI Hardening**: Implements weight-free scrolling and `imePadding()` for 100% keyboard-aware accessibility in landscape mode.
    - **Licensing UX**: Implements `LicenseKeyTransformation` for non-intrusive `XXXX-XXXX-XXXX-XXXX` visual formatting and **Bi-Directional QR Support** (Scan License Key / Generate Device ID QR).
- **`AdminPanel.kt`**:
    - Features: Sectioned management, storage monitoring, and **Performance-Limited History** (Recent 10).
    - **Watermark Customization**: Live logo upload and 4-corner position selection (Top-Left, Top-Right, Bottom-Left, Bottom-Right).
    - **Optimized Review**: Tap-to-Reveal email privacy and dedicated **"View All"** handoff to `FullHistoryPane`.
- **`FullHistoryPane.kt`**:
    - Dedicated view for complete database audit and bulk log maintenance.

### 📺 TV Layer (Package: `com.pbcam.tv`)
- **App ID**: `com.pbcam.tv` (Ensures unique deployment identity).
- **`MainActivity.kt`**: Entry point optimized for Leanback; initializes the `TvDashboardScreen`.
- **`TvDashboardViewModel.kt`**: 
    - **Cloud Bridge**: Persistent listener on `live_status/{pairedId}`.
    - **Logic**: Orchestrates transitions between Live Feed, Local Replay, and Cloud Replay; monitors tablet online presence with an **automated 10s retry engine**.
    - **Observability Audit**: Displays "Last Sync Time" to help troubleshoot connection jitter.
    - **Replay State Machine**: Manages the multi-stage replay lifecycle: `LOADING` (Animation) -> `PLAYING` -> `COMPLETE` (Prompt with 20s countdown).
- **`TvDashboardScreen.kt`**: 
    - **VideoPlayerLayer**: High-performance Media3 wrapper prioritizing **Sub-Stream (stream2)**; synchronized to auto-stop during tablet downtime or active replays. Implements **Auto-Negotiation (UDP/TCP)** to prevent connection conflicts with the Tablet's recording stream.
    - **BrandingOverlay**: Renders official lower-center logo/tagline.
    - **DigitalClockOverlay**: Renders the digital clock anchored to the **Bottom-Right**.
    - **SettingsButton**: Renders the D-Pad focusable gear icon anchored to the **Bottom-Left**.
    - **AdminPanelDialog**: Renders the comprehensive technical diagnostic dashboard and pairing management.
    - **Diagnostic/Alert Layer**: Renders high-priority brand-aligned "TABLET OFFLINE" popups with auto-retry countdowns and **Emergency Pairing** recovery.
    - **Status Banner**: Professional detached pulsing bubbles for high-visibility public view (Top).

### ⚙️ Engine Layer (Package: `com.pbcam.app.service` & `worker`)
- **`CloudClients.kt`**:
    - **`DriveUploader`**: Implements resumable match uploads and `findReleaseApkAnonymous` for zero-friction system updates.
- **`RecordingService.kt`**:
    - **Lifecycle Service**: Claims hardware ownership during matches.
    - **Logic**: 
        - **Internal**: CameraX with 6Mbps target bitrate and hardware synchronization.
        - **RTSP**: Hardened FFmpeg capture with **High-Speed Stream Copying** (`-c copy`), Jitter-Proof Wireless Protocol (50MB buffer), and monotonic clock synchronization.
        - **Handoff (Rule 3.4)**: Implements a **100ms capture delay** on pause to ensure visual continuity.
        - **Log Integrity**: Implements `lastExecutedSession` persistence to capture real FFmpeg exit codes and logs even during loop failures.
    - **Initialization Fix**: Utilizes universal `-timeout` flag (normalized from legacy `stimeout`) for wide camera compatibility.
    - **Fatal Error Guard**: Implements 2-second failure detection. Automatically stops matches on instant connection failure to prevent empty segment loops.
    - **Optimization (Round 25)**: Implements **Micro-Segmenting** (10-minute rotation) for RTSP resilience and **Dynamic Buffer Scaling** (LAN vs. WAN) for memory efficiency.
    - **Stability**: Indefinite `PARTIAL_WAKE_LOCK`, descriptive file naming initialization, and notification-based exit controls.
- **`ConvertWorker.kt`**:
    - **Pipeline**: Hardened part discovery -> Atomic concatenation -> **Full-Hardware Watermarking** (all sources).
    - **Turbo Mode**: Utilizes `h264_mediacodec` for both decoding and encoding to maintain high-speed post-processing.
    - **Atomic Handoff (Round 25)**: Replaced static delays with a **File Stability Protocol**, verifying file readiness across multiple intervals before starting processing.
    - **Motion Smoothing**: Implements mandatory **`fps=30`** filter and **`setpts=PTS-STARTPTS`** normalization to eliminate frame skipping/jumping caused by source jitter.
    - **Dynamic Branding**: Supports custom PNG logo overlays with user-defined 4-corner positioning (20px padding) and static logo pre-scaling.
    - **Smart Detection**: Integrates `FFprobe` to detect and preserve source frame rates (e.g., 60fps) in the final output.
    - **Encoder Hardening**: Forces `-maxrate 8M -bufsize 20M` to ensure hardware encoder stability during high-action points.
    - **Session Silos**: Uses session-unique temporary filenames (e.g., `proc_{id}_source.mp4`) to ensure absolute isolation between consecutive matches.
    - **Anti-Stutter**: Enforces monotonic timestamps and constant frame rate for broadcast-quality output.
- **`UploadWorker.kt`**:
    - **Logic**: Resumable Drive uploads -> Branded Gmail notification -> Atomic cleanup of source parts using descriptive naming filters.
    - **API Diagnostics**: Propagates raw Google API error codes (`403`, `401`, `400`) directly to UI state and prevents infinite token retry loops on unauthenticated/unauthorized projects.
    - **Fault-Tolerant Delivery**: Classifies HTTP 4xx email errors (invalid recipient) as non-fatal to preserving footage, completing session (`READY_EMAIL_FAILED`) with Google Drive link intact.

### 🔐 Data & Security (Package: `com.pbcam.app.data`)
- **`WatermarkPosition.kt`**:
    - **Schema**: Defines `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT` enum for overlay placement.
- **`SecurityUtils.kt`**:
    - **Logic**: Hardware-locked license generation, XOR obfuscation, and **Professional Naming Engine** (`[masked_email]_[date].mp4`).
    - **Multi-Masking**: Corrected individual masking for sessions with multiple players (comma-separated).
    - **Licensing Standards**: Enforces the `PB-XXXX-XXXX` ID format and unified raw-hex hashing logic.
- **`SettingsStore.kt`**:
    - **Persistence**: Obfuscated storage for Dual-Stream RTSP URLs, passcodes, and match guardrails.
    - **Retention Policy**: Persists configurable `retentionDays` for automatic local and cloud cleanup.
- **`RecordingRepository.kt`**:
    - **Data Access**: Central hub for match logs and pipeline status.
    - **Retention Management**: Implements `purgeOldSessions` to keep local logs in sync with cloud storage limits.

---

## 🔄 3. Critical Data Flows

### Match Recording Lifecycle:
1.  `DashboardScreen` triggers `ACTION_START` -> `RecordingService`.
2.  `RecordingService` generates **Descriptive Filename** -> Starts synchronized `_part{index}` capture.
3.  **Sleep Prevention**: `MainActivity` detects recording state -> Locks screen to ON.
4.  User hits STOP -> `RecordingService` unbinds hardware -> Enqueues `ConvertWorker`.
5.  **Waiver Handoff**: Dialog appears -> User clicks "I AGREE" -> `DashboardViewModel` triggers **Auto-Preview**.
6.  `ConvertWorker` detects Octa-core CPU -> pre-scales logo -> applies **Subtle Watermark** with **CFR 30fps** -> Enqueues `UploadWorker`.
7.  `UploadWorker` uploads to Drive -> Triggers Gmail -> Deletes source parts.

### The "Frozen Frame" (Immersive Continuity):
1.  **Direct Texture Capture**: RTSP preview uses a `TextureView` (required for bitmap access) within a 1-second `LaunchedEffect` capture loop.
2.  **State Persistence**: `DashboardViewModel` preserves the `lastPreviewFrame` during match transitions and setting refreshes to avoid black screens.
3.  **Handoff Logic**: On Stop/Pause: Feed layer removed -> Dashboard shows dimmed `lastPreviewFrame` (Layer 1.5).

---

## 🛡️ 4. Security & Privacy Model
- **Licensing**: Heartbeat system monitors Firebase node `licenses/{deviceId}`. <1s revocation response.
- **Log Security**: Automated regex-based sanitization in `SecurityUtils` ensures RTSP credentials and PII are never committed to the system logcat.
- **Data Organization**: Recordings are organized by masked email and date to protect player privacy while ensuring traceability on cloud storage.
- **One-Tap Control**: Foreground notifications include a hard-kill "EXIT APP" button for absolute user control.

---

## 📝 5. Developer / AI Notes
- **Golden Build Rule**: Always run `app:assembleDebug` after UI or logic changes.
- **Media Standard**: Always force `-vsync cfr` and `-pix_fmt yuv420p` for global device compatibility.
- **Naming Standard**: Always use `SecurityUtils.generateSessionFileName` for all output artifacts.
- **Visibility Standard**: Always use `pronounced shadows` for UI text overlaid on camera feeds.
- **Performance Standard**: Always leverage `availableProcessors` for threading and pre-scale visual overlays.
