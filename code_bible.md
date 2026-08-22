# SeenMyPickle: Code Bible & Architecture Documentation (2026 Golden Build)

## 1. Project Overview
SeenMyPickle is a professional Android application designed for high-fidelity pickleball court monitoring. It features a robust multi-source recording engine (RTSP/Internal/USB), a secure hardware-locked licensing system with real-time remote revocation, and a fully automated background processing pipeline optimized for mid-range tablets and mobile phones.

## 2. Mandatory Workflow Rules (Gemini/AI Instructions)
ALL AI-assisted development is governed by the rules defined in [ai_workflow_rules.md](file:///C:/Users/iamre/AndroidStudioProjects/PBCam/ai_workflow_rules.md). 

Key highlights include:
1.  **Implementation Plans** required for all changes.
2.  **Impact Analysis** mandatory for every proposal.
3.  **Bug Fix Logging** in `bug_fixes_history.md`.
4.  **Post-Fix Updates** to Code Bible/Map upon user approval.

## 3. Core Architecture & Workflow

### 3.1 Styling & Branding
- **Color Strategy**:
    - **Neon Brand**: Signature `PickleGreen` (#99FF00) for Dark/Midnight themes.
    - **Branding Bubbles**: All top-level UI containers (Header, Recording Card, Action Icons) MUST use the standard branding bubble style: `Color.Black.copy(alpha = 0.6f)` with a minimum **8dp shadow** and **16dp rounded corners**. This ensures legibility against dynamic camera backgrounds.
    - **Light Mode Accessibility**: Must use high-contrast palette (`PickleGreenDark` - #2E7D32) and Burnt Orange accents for readability.
- **Active State Feedback (Broadcast Mode)**:
    - **Recording Status Banner**: A top-center semi-transparent banner MUST display the match status and duration (e.g., **"MATCH LIVE • 00:09"**) with a pulsing red REC dot during active capture.
    - **Banner Centering**: The banner MUST be perfectly centered horizontally using `Alignment.TopCenter` logic.
    - **Pause Clarity**: The banner MUST switch to a yellow **"MATCH PAUSED"** status when recording is suspended. This applies to both the top central banner (Internal/USB) and the center-screen notification card (RTSP).
    - **Active Glow**: The Recording Control Card MUST use a **Red Outer Glow** (Shadow) during live matches to provide high-visibility feedback.
    - **Immersive Border**: A subtle red pulsing border MUST surround the entire screen container while a recording is active.
    - **Banner Prominence**: The central banner MUST use a large, high-impact font (`headlineMedium`) and clear borders for court-side visibility.
    - **Total Centralization (RTSP)**: When an RTSP recording is active, the top central banner is HIDDEN. All match information (Status: **"MATCH LIVE"**, Quality Context: **"LIVE PREVIEW PAUSED"**, and Time: **"RECORDING 01:03"**) MUST be unified into the single center-screen notification card to eliminate visual redundancy.
    - **Source-Specific Header**: The top central banner MUST still display for Internal/USB sources to maintain status awareness, as these sources do not utilize the central quality notification.
- **Iconography & Visual Assets**:
    - **Minimalist Controls**: Primary actions in the dashboard (Pause, Admin, Mute) must use **icon-only** interfaces. Controls MUST dynamically scale based on device type: **56dp** for mobile and **72dp** for tablets.
            - **Safe-Zone Icon Standards**: 
        - The app icon foreground MUST use a centered `layer-list` drawable (`ic_launcher_foreground.xml`) referencing the high-resolution logo.
        - Centering MUST be enforced using `android:gravity="center"` and a target width/height of **72dp** to prevent launcher cropping and ensure perfect alignment across all Android versions.
        - The background MUST be a solid black vector (`ic_launcher_background.xml`) to maintain the "Golden Build" high-contrast aesthetic.
    - **Branding Bridge & Diagnostics (Splash)**:
        - **100% Reliable Bridge**: Splash screen MUST use a dedicated `SplashActivity` with a 2-second branding duration to bypass Android 12+ splash restrictions.
        - **Asset Standards**: MUST use the full-screen `splash_full` (Full Artwork) as a cropped background image.
        - **Diagnostic Overlay**: A System Readiness Check container MUST be anchored to the bottom-center (`bottom = 20.dp`) to prevent blocking the central "YOUR PLAY IS RECORDED." motto.
        - **High-Contrast Diagnostics**: The diagnostics container MUST use a solid black background with **0.85f opacity** and a prominent **16dp shadow** to ensure readability against complex artwork.
        - **Diagnostic Indicators**: Real-time status for Network, Camera, Google, and Storage MUST be checked before transition to the main dashboard.
- **Label Standardization**:
    - Primary identification field must use the exact string: **"Email address"** for both label and placeholder.
- **Branding & Header**:
    - **Safe Center Layout**: Header must be split (Brand Left, Actions Right) with an empty center.
    - **Absolute Top Snapping**: On mobile, the header MUST use zero vertical padding to "snap" the logo and power button to the top edge for maximum court visibility.
    - **Edge-to-Edge immersion**: The dashboard MUST use `enableEdgeToEdge()` logic. The camera feed background MUST draw behind the Android Status Bar.
    - **Selective System UI**: The Android Navigation Bar (Home/Back) MUST be hidden during active monitoring, while the Status Bar (Clock/Battery) MUST remain visible and floating above the feed.
    - **Pairing Visibility**: The dashboard header MUST display the **TV PAIRING ID** in the top-left branding pill. This ID is high-contrast PickleGreen and allows for 100% accurate manual pairing with the TV app.
    - **High-Contrast Legibility**: 
    - All text and icons in the top-level overlays (Header, Actions) MUST use **pronounced black drop shadows** (Offset 3f, 3f / Blur 6f) to ensure legibility against bright camera feeds.
    - **Header Branding Background**: The top-left branding area (Logo, Status, Court Tag, Device ID, and Progress) MUST be wrapped in a high-contrast `Surface` with **0.4f opacity** black background, **8dp shadow**, and **16dp rounded corners** to match the dashboard icon aesthetic while ensuring 100% legibility regardless of the camera background.
- **Aspect Ratio Hardening (RTSP)**: RTSP feeds MUST use the native `PlayerView` with `RESIZE_MODE_ZOOM` (Center Crop) to ensure 100% background coverage on all device aspect ratios.
- **Adaptive Ergonomics**:
    - **Engine Logic**: Dashboard MUST use a conditional layout engine based on `smallestScreenWidthDp >= 600` (Tablet).
    - **Tablet Rule (Side-by-Side)**: Controls MUST be anchored to the bottom corners (Settings Bottom-Start, Match Controls Bottom-End) to leverage the wide screen space.
    - **Mobile Rule (Stacked)**: Controls MUST be stacked vertically in the bottom-center (Match Controls above Settings Cluster) to prevent horizontal clashing and optimize one-handed thumb reach.
    - **Relative Widths**: Input fields (Email) MUST use relative scaling: `fillMaxWidth(0.35f)` on mobile to prioritize court visibility, while maintaining fixed widths (400dp) on tablets.

### 3.2 UI Performance & State Management
- **Local Input Buffering**: High-frequency text inputs (like the Player Email field) **must** use local `mutableStateOf` buffering within the Composable. Decouple physical typing from ViewModel/Database writes to ensure zero-latency keyboard response on mid-range hardware.
- **Input Field Styling**: The primary email identification field MUST use an `OutlinedTextField` with a **transparent background** (`focusedContainerColor = Color.Transparent`) to maintain card aesthetic continuity.
- **Input Persistence**: Critical ephemeral UI states (specifically the **Player Email** field) MUST use `rememberSaveable` to ensure data persists during device rotation and configuration changes.
- **Resource Hardening**: 
    - Assets accessed by background workers or system-shared libraries (like the email logo) **must** be stored in `res/raw`. 
    - Always use direct resource IDs (`R.raw.file`) instead of dynamic string lookups (`getIdentifier`) to prevent "No package ID 6a" collisions with shared libraries like WebView/GMS.

### 3.3 Real-Time Licensing & Security
- **Product Activation**: Forces hardware-locked license check before configuration. Every new install MUST display and require agreement to the **Setup Disclaimer** (Requirements & Privacy) before activation. Activation supports both manual 16-character key entry and **QR Code Scanning** via ML Kit.
- **Licensing Source of Truth (Absolute Resilience)**:
    - The **Local Cryptographic Key** is the primary driver of the `isLicensed` state. 
    - Firebase is used strictly as a **Negative Filter** (Revocation or Expiry).
    - The app MUST NOT wait for a `"status": "active"` signal from the cloud to enable the UI. This eliminates sync-induced "flicker" during activation.
- **Hybrid Monetization (Proposed)**: The system architecture is designed to support a unified "Premium" status unlocked by either a **Monthly Subscription** or a **One-Time Lifetime Purchase** via Google Play Billing. Legacy Firebase-locked licenses remain as a valid fallback.
- **Startup Performance**: 
    - Critical initialization (settings loading) MUST happen synchronously in the ViewModel `init` block to dismiss the splash screen instantly. Heavy background handshakes (licensing, session recovery) MUST be delayed or moved to the IO thread to prevent "Logo Hangs" on mid-range hardware.
    - **UI State Optimization (Round 25)**: High-frequency UI updates (e.g., Match Duration Timer) MUST be decoupled from the main `DashboardUiState`. These states MUST use standalone `StateFlow` streams to prevent unnecessary full-screen recompositions during active recording.
    - **Auto-Preview**: Upon a successful, licensed launch on a configured device, the system MUST automatically trigger the 5-minute Idle Preview to provide instant dashboard feedback.
- **License Validity**: Supports time-limited licenses (7 Days, 30 Days, 1 Year, etc.). The client app MUST monitor the `expiryTime` field in the Firebase license node.
- **Automatic Expiry**: Once `System.currentTimeMillis() > expiryTime`, the app must trigger an immediate lockout with the "License Expired" message, identical to the revocation flow.
- **License Recovery**: Successful activations (including re-activations after a data wipe) MUST trigger a **Cloud Heartbeat** to the Firebase path `licenses/{deviceId}`. This updates the `status` to `"active"` and sets the `lastCheckIn` timestamp. The heartbeat MUST be throttled to **once per hour** to save battery and network data.
- **Hardware Binding**: Uses `ANDROID_ID` for unique `XXXX-XXXX` fingerprints. The raw alphanumeric ID is used for user-facing display and copy-to-clipboard actions to ensure 1:1 compatibility with the license generator.
- **License Logic Unification**:
    - **Raw Hashing**: All platforms (Main App, KeyGen, Web) MUST perform cryptographic hashing on the raw device ID.
    - **Verification Gate**: The app MUST use full cryptographic verification (`SecurityUtils.verifyLicense`) rather than simple string prefix checks for activation.
- **Admin Security**:
    - **Passcode Force**: Authorization MUST use a strict **4-digit numeric** requirement. Non-digit inputs MUST be blocked, and the "Authorize" button MUST only enable upon the 4th digit.
    - **Recovery Access**: A master hardcoded passcode (**2026**) is maintained for emergency administrative recovery.
    - **Persistent Lockout**: Three failed PIN attempts trigger a mandatory **60-second lockout**. This state MUST be stored in the obfuscated `SettingsStore` to ensure it survives app restarts.
    - **Session Expiry**: Admin sessions must be terminated immediately upon closing the Admin Panel.
- **Log Sanitization**:
    - **Credential Safety**: All RTSP URLs and session logs MUST be passed through `SecurityUtils.sanitizeLogs()` before being sent to `Log.d` or `Log.e`. This prevents `user:password` cleartext leaks in system logcat.
- **Instant Revocation**: App maintains a persistent `ValueEventListener` for instant (<1s) shutdown of active recordings if a license is revoked.

### 3.4 Dashboard & Tablet Experience
- **Orientation Control**:
    - **Dynamic Orientation (Golden Build)**: 
        - The Setup Wizard MUST allow **Portrait** orientation on mobile devices to improve one-handed configuration.
        - The Dashboard MUST force **Landscape** (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`) on all devices to maintain professional court monitoring standards.
        - The app MUST automatically rotate and lock to Landscape the moment the Setup Wizard is completed.
        - Tablets (`smallestScreenWidthDp >= 600`) MUST remain in Landscape throughout the entire application lifecycle.
- **Sleep Prevention (Screen-On)**:
    - **Global Enforcement**: The app MUST use `FLAG_KEEP_SCREEN_ON` globally as long as the application is in the foreground. This ensures the dashboard remains visible for court monitoring without the device locking.
    - **Implementation**: Managed at the `MainActivity` level using a `SideEffect` to ensure the window flag is always active.
- **Dynamic Input Ergonomics**:
    - **Keyboard Awareness**: Dashboard MUST use `WindowInsets.ime` to track software keyboard state.
    - **Active Centering**: The Recording Control Card MUST transition its alignment from `BottomEnd` to `Center` whenever the keyboard is visible to prevent occlusion and optimize visibility.
    - **Setup Wizard Scroll Guard**: The Setup Wizard MUST NOT use `Modifier.weight(1f)` on step containers. It MUST use `verticalArrangement = Arrangement.Top` and `imePadding()` on its main scrollable container to ensure all input fields and navigation buttons remain visible and reachable above the software keyboard.
- **USB Peripheral Support**:
    - **External Selection**: When `CameraSource.USB` is selected, the app must explicitly request `LENS_FACING_EXTERNAL` via CameraX.
    - **Graceful Fallback**: If no external camera is detected, the system must fallback to the default back camera to prevent a "black preview" state.
- **Responsive Navigation**: Use `imePadding()` and `navigationBarsPadding()` to handle soft keyboards and system bars gracefully.
- **String Formatting Standard**: 
    - Critical UI messages or log strings containing code-like templates (e.g., `${}`) MUST use `String.format()` or explicit string concatenation. 
    - This prevents AI/tool-based escaping artifacts from displaying literal backslashes on the user's screen.
- **Rotation Resilience**: ALL camera preview components (Internal/RTSP) MUST handle configuration changes (rotation) without losing the feed. Implementation MUST include auto-rebinding logic in the `AndroidView.update` block to ensure hardware continuity.
- **RTSP Preview Lifecycle**: To ensure 100% resource availability and diagnostic transparency, the Dashboard Preview MUST utilize a **Consolidated Lifecycle** (single `LaunchedEffect`). It MUST provide **On-Screen Status Diagnostics** (e.g., "Connecting...", "Buffering...") and implement an **Ultra-Low Latency LoadControl** (min 1500ms) to minimize black screen hangs on slow Wi-Fi.
- **Recording Guardrails**:
    - **Duration Limit**: Recordings must be capped at a configurable maximum (Default 120 minutes). The `RecordingService` must automatically finalize and upload the session upon reaching this limit. The limit MUST be enforced across all match segments using a session-wide timer to ensure data integrity and storage efficiency. Entry MUST support high-precision manual minute input.
    - **Micro-Segmenting (Round 25)**: To prevent catastrophic data loss during long recordings, the RTSP capture engine MUST automatically rotate segments every **10 minutes**. This limits potential data loss to the active segment only.
- **Dynamic Preview Timeout**:
    - **Customizable Logic**: Administrators MUST be able to configure preview timeout durations via the Admin Panel.
    - **Active Match**: To save maximum power while the service is busy recording, the screen feed SHOULD automatically hide after a configurable period (Default: 1 minute).
    - **Idle/Break**: When not recording (or during a pause), the preview SHOULD stay active for a configurable period (Default: 5 minutes) to allow for comfortable court monitoring.
    - **State Transition**: If a match starts while a longer idle preview is active, the system MUST automatically downgrade the remaining time to the recording timeout to prioritize battery.
- **Extreme Observability (Progress & Pipeline)**:
    - **Lifecycle Tracking**: Background tasks (Conversion & Upload) MUST be tracked in the dashboard header.
    - **Auto-Dismiss Rule**: Success/Failure status indicators (Header Progress Bar) MUST persist for **5 seconds** after completion before automatically hiding.
    - **Explicit Nullification**: UI states for progress (e.g., `uploadProgress`) MUST use nullable types (`Float?`) to distinguish between active-at-0% and inactive states.
- **Immersive Continuity**:
    - **No Black Screen**: The dashboard MUST NOT turn into a blank black canvas when a preview stops or is paused.
    - **TextureView Requirement**: RTSP previews MUST use a `TextureView` to enable direct `Bitmap` capture for the frozen frame layer.
    - **Capture Guard (Anti-Ghosting)**: The snapshot capture loop MUST be automatically suspended whenever the software keyboard is visible (`WindowInsets.ime`). This prevents "burning" the shifted UI elements into the dashboard's background street scene.
    - **Periodic Capture**: Both Internal and RTSP previews MUST implement a 1-second background capture loop (via `LaunchedEffect`) while active to ensure the `lastPreviewFrame` is always current.
    - **Frame Retention**: The UI MUST capture the last frame of the camera feed and display it as a background with a **60% dark tint** during idle, standby, or pause states.
    - **Privacy Wipe**: All player emails and temporary alert settings MUST be explicitly nullified (`alertEmail = ""`) and cleared from both the ViewModel state and persistent `SettingsStore` the moment a match is stopped or the dashboard returns to idle.
    - **Control Card Streamlining**: The recording control card MUST NOT display a "Match Recording" title. The interface MUST focus exclusively on player email entry and action buttons to ensure a clean, purpose-driven user experience.
    - **Battery Optimization (Pause)**: When a match is paused, the app MUST finalize the current recording segment and release the camera hardware (unbind for Internal, cancel for RTSP) to save power. 
    - **Capture Buffer**: A minimum **100ms capture delay** MUST be enforced between the "Pause" trigger and the "Hardware Release" to ensure the UI successfully captures the final valid court frame.
    - **Resume Logic**: Upon Resume, a new segment MUST be initiated and automatically combined with previous parts during match finalization.
    - **Memory Safety**: Retained frames MUST be cleared when switching camera sources or closing the app to prevent memory leaks and visual artifacts.
- **Player Email Auto-Reset**: The "Player Email" field and chip list MUST be automatically cleared as soon as a recording is stopped.
- **Multi-Player Support**:
    - **Capacity**: The UI MUST support adding up to **5 player emails** per session.
    - **Interaction**: Added emails MUST be displayed as removable `AssistChip` components within the recording control card.
    - **Validation**: "START MATCH" MUST only enable if at least one valid email is provided (either typed or in chips).
    - **Smart Combination**: The system MUST combine added chips and the currently typed email into a single recipient list (max 5) when starting a match.
    - **Input Handoff**: The email text field MUST automatically clear after an email is added as a chip.
    - **Visual Discovery**: The email input field MUST include a trailing **"+" icon** when a valid email is present and space is available in the recipient list. This provides a clear visual cue for adding multiple players.
- **Auto-Preview Handoff**: Upon acknowledging the post-match "Recording Notification" (Waiver), the app MUST automatically trigger the 5-minute Idle Preview to prepare the monitor for the next players.
- **Z-Index Strategy**: UI must follow a strict layering model:
    1. **LAYER 0 (Floor)**: Background MP4 Player.
    2. **LAYER 1 (Live Feed)**: Camera Preview (RTSP or Internal).
    3. **LAYER 1.5 (Continuity)**: Retained Frozen Frame (Dimmed).
    4. **LAYER 2 (Dashboard)**: Floating Controls and Corner Icons.
    5. **LAYER 200 (Admin)**: Full-screen settings overlap (Surface-based).

### 3.5 Recording & Background Pipeline
- **Dual-Stream Strategy**: Dahua/Hikvision cameras SHOULD be configured with separate URLs.
    - **Main Stream**: High-resolution (1080p/4K) exclusively for high-fidelity recording.
    - **Sub Stream**: Low-resolution (720p/D1) for low-latency, battery-efficient dashboard preview.
- **Automated Configuration**: 
    - The system MUST automatically populate both Main and Sub RTSP fields upon successful Auto-Scan and credential entry. 
    - Auto-fill patterns: `/stream1` for Main and `/stream2` for Sub (Standard ONVIF/Dahua/Hikvision).
- **Smoothness Hardening (FFmpeg)**: 
    - **Wireless Resilience Protocol**: All capture commands MUST utilize a **50MB Jitter Buffer** (`-buffer_size 52428800`) and a **1024-packet Reorder Queue** (`-reorder_queue_size 1024`) to absorb Wi-Fi network jitter (specifically for Tapo/Consumer cameras).
    - **RTSP Handshake Hardening**: MUST utilize `-stimeout 15000000` (15 seconds) for all RTSP connections to accommodate slow Digest Authentication and high-resolution stream initialization.
    - **Buffer Scaling (Round 25)**: The system MUST dynamically scale the jitter buffer based on the RTSP IP locality. Local/LAN IPs (192.168.x, 10.x) SHOULD use a reduced **25MB buffer** to save memory, while maintaining the full 50MB for remote/wireless sources.
    - **Clock Synchronization**: MUST enforce `-use_wallclock_as_timestamps 1` and `-fflags +igndts+genpts` to ignore jittery source clocks and ensure monotonic frame delivery.
    - **Transport**: Force `-rtsp_transport tcp` and `-err_detect ignore_err` with `-fflags +discardcorrupt` to prevent encoder hangs during Wi-Fi signal drops.
- **Internal Bitrate Balancing**: Local camera recordings MUST use a **6Mbps** target bitrate to prevent I/O write pressure and ensure hardware stability on mid-range devices.
- **Smoothness Hardening (Preview)**: All RTSP previews MUST use the **Strict TCP Standard** (`setForceUseRtpTcp(true)`) and a standard User-Agent (`SeenMyPickle/1.0`). This prevents `SETUP 400` negotiation errors common with consumer-grade wireless cameras. Audio MUST be disabled for preview stability.
- **RTSP Surface Management**: All RTSP previews MUST use a `TextureView` as the primary rendering surface. This is mandatory to ensure direct access to the `bitmap` for frame capture (Frozen Frame continuity).
- **Surface Resilience**: Do NOT use fragile view-hierarchy manipulation (e.g., `getChildAt(0)`) to replace `PlayerView` surfaces. Attach `ExoPlayer` directly to the `TextureView` using `setVideoTextureView`.
- **Snapshot Frequency**: Live snapshots for continuity MUST be captured at a minimum frequency of **1 second** (via `LaunchedEffect`) during active previews.
- **Aggressive Resource Handoff**: 
    - The UI (`DashboardScreen`) MUST completely shut down all camera previews (Internal/RTSP) the moment a match starts.
    - This ensures 100% of the device SOC and hardware media codecs are available to the `RecordingService` for zero-stutter output.
- **Hardware-Accelerated Pipeline**:
    - **Capture-Time Stability (RTSP)**: RTSP sources MUST use high-speed stream copying (`-c copy`) during capture to prevent frame loss and visual stutter. Watermarking and compression are deferred to the post-match phase.
    - **Monotonic Timeline Reconstruction (CFR)**: RTSP sources MUST be re-clocked to a perfect 30fps during post-processing using `-r 30 -vsync cfr`. This eliminates "judder" caused by jittery wireless camera timestamps.
    - **Dynamic Codec Discovery**: The `ConvertWorker` MUST use `FFprobe` to identify the source codec (H.264/H.265) before processing. **Forcing an input hardware decoder is strictly prohibited** to ensure compatibility with varied camera stream formats.
    - **Mediacodec Priority**: Use `h264_mediacodec` for **encoding** in the `ConvertWorker` to maximize processing speed.
    - **Dynamic Watermark Customization**:
        - **Flexible Positioning**: Administrators MUST be able to choose between **Top-Left**, **Top-Right**, **Bottom-Left**, and **Bottom-Right** for watermark placement.
        - **Position-Specific Padding**: All watermark positions MUST maintain a standard **20px padding** from the edge of the video frame.
        - **Custom Branding**: The system MUST support user-uploaded PNG logos. Custom logos MUST be stored in the app's internal storage (`filesDir/watermarks`) to ensure persistence and accessibility for background workers.
    - **Watermark Standard**: Use the high-quality PNG logo. If no custom logo is provided, fallback to the default branding from `res/raw`.
    - **Zero-Latency Tuning**: Disable B-frames (`-bf 0`) and use an **8Mbps** target bitrate (12Mbps peak) for professional quality without processing lag.
    - **Post-Process Motion Smoothing**: All watermarking and transcoding pipelines MUST implement the **`fps=30`** filter and **`setpts=PTS-STARTPTS`** normalization. This is mandatory to eliminate "jitter" or "skipping" caused by jittery source timestamps from wireless cameras.
    - **Hardware-Accelerated Encoder Stability**: Mediacodec encoders MUST utilize a minimum **8Mbps maxrate** and **20Mbps bufsize** (`-maxrate 8M -bufsize 20M`) to provide sufficient encoding headroom for high-motion pickleball play.
    - **Turbo Processing (Round 16)**: 
        - The worker MUST dynamically detect the device CPU core count (`availableProcessors`) and pass it to the FFmpeg `-threads` flag to ensure 100% hardware utilization.
        - **FPS Detection**: The pipeline MUST use `FFprobe` to detect the source frame rate and preserve it (e.g., 60fps) to maintain high-fidelity motion.
        - Use `-thread_queue_size 16384` during the watermarking phase to prevent buffer overflows on high-motion court footage.
        - Watermark logos MUST be pre-scaled ONCE before the main encoding loop to eliminate per-frame filtering overhead.
    - **Stutter-Free Output**: ALL watermarking/re-encoding MUST force a Constant Frame Rate (CFR) using `fps=30` and `-vsync cfr` to ensure smooth playback on all devices and cloud players.
    - **Audio Hardening**: All recordings MUST capture and transcode audio to **AAC (96k, 44100Hz)** for an optimal balance of court-side fidelity and file size.
    - **Audio/Video Sync**: MUST implement `-af "aresample=async=1"` to prevent timestamp drift from causing video stalls in long recordings.
    - **Visual Lossless Compression**: All capture and processing MUST utilize **Variable Bitrate (VBR)** with a 5Mbps target and 10Mbps peak to reduce file sizes by 30-50% without noticeable quality degradation.
    - **Maximum Compatibility**: ALL processed MP4 files MUST use `-pix_fmt yuv420p` and `-profile:v high -level 4.1` to ensure playback reliability across all devices.
    - **Session Hardening**: All background workers MUST use **session-unique temporary filenames** (e.g., `proc_${sessionId}_source.mp4`) to prevent data corruption between rapid consecutive recordings.
    - **CRITICAL: No Presets**: The `-preset` flag is strictly prohibited for hardware encoding.
- **Dual-Ownership Camera Binding**:
    - **Idle State**: The UI (`DashboardScreen`) MUST handle its own local CameraX binding to the Activity lifecycle for 100% reliable preview feedback.
    - **Recording State**: Hardware ownership is handed off to the `RecordingService`. The service ONLY claims the camera when a match is active.
    - **Thread Safety**: Every `unbindAll()` and `bindToLifecycle()` call MUST occur on the **Main (UI) Thread** to prevent hardware deadlocks.
- **Extreme Observability**: 
    - Pipeline progress MUST be tracked from Match Start to Email Delivery.
    - **Status Sequence**: `RECORDING` -> `FINALIZING` -> `PROCESSING` -> `UPLOADING` -> `SENDING`.
    - **Header Bar**: A high-visibility, 300dp (tablet) / 200dp (mobile) pulsing progress bar MUST be visible in the dashboard header.
    - **Selective Visibility**: The header progress bar MUST ONLY be visible when a background pipeline task is active (e.g., after a match ends). It MUST NOT display redundant "Recording Live" text during an active match.
    - **Auto-Dismiss**: The progress bar and status message MUST automatically vanish **5 seconds** after reaching `COMPLETED` or `FAILED` to keep the UI clean.
- **Instant Initialization Feedback**: UI components monitoring background work MUST provide a fallback state (e.g., "Initializing pipeline..." at 5% progress) as soon as work is enqueued. Waiting for the worker's first internal progress broadcast is prohibited to prevent UI "flickering" or perceived processing gaps.
- **Naming Standard Synchronization**: 
    - Services and Workers MUST use a shared, descriptive naming convention generated via `SecurityUtils.generateSessionFileName`.
    - Format: `[obscured_email]_at_[domain]_[YYYY-MM-DD_HHmm].mp4`.
    - Example: `p*******l_at_gmail.com_2026-08-03_1430.mp4`.
    - Segments MUST append `_part{index}.mp4` or `.ts` to this descriptive base name to ensure 100% discovery reliability during processing handoffs.
- **Persistent Error Visibility**: Critical failures during background processing MUST be held in the primary dashboard UI state for at least 5 minutes (or until acknowledged) to ensure admin awareness.
- **Pipeline Handoff Hardening (Atomic Delivery)**:
    - **Atomic Shutdown**: `RecordingService` MUST explicitly wait for the recording loop to finalize and background workers to be enqueued before calling `stopSelf()`.
    - **Atomic Handoff (Round 25)**: The background pipeline MUST NOT rely on static delays (e.g., 5s cooldown). `ConvertWorker` MUST implement a **File Stability Protocol**, monitoring segment file sizes across 3x1s intervals to confirm the OS has released all file handles before concatenation begins.
    - **Fresh Token Protocol**: Background workers performing multi-stage cloud tasks (e.g., Upload followed by Email) MUST re-request the Google Access Token right before the final delivery stage to prevent expiration during long uploads.
    - **Socket Release**: When a match starts, stops, or pauses, the Dashboard MUST aggressively release all camera resources (network sockets and hardware decoders). The `RecordingService` MUST NOT begin its capture loop until the UI has confirmed resource disposal.
    - **Concat Hardening**: Combining parts MUST use `-fflags +igndts+genpts` and `-avoid_negative_ts make_zero` to eliminate visual stutter and timestamp drifts.
- **Data Integrity & Success Logic**:
    - **Atomic Cleanup**: Source files (`.ts` parts) must **only** be deleted after a verified successful cloud upload. If a processing failure is detected (e.g., output < 1MB), the segments MUST be preserved in internal storage for manual recovery.
    - **Retention Sync**: Local session logs and Google Drive footage MUST be automatically purged based on the administrator's configured `retentionDays` setting (Default 5 days). This logic is triggered daily via `MaintenanceWorker` and instantly upon every application cold launch in `DashboardViewModel`.
    - **Rescue Mode**: If hardware-accelerated watermarking produces black frames or fails, the worker must automatically fallback to raw stream delivery using `-c copy -movflags +faststart`.
- **Local Replay Server**:
    - **Socket Resilience**: The server MUST enable `reuseAddress = true` to prevent `EADDRINUSE` errors during match transitions or app restarts.
- **Email Communications**:
    - **Branded structure**: All emails must use `multipart/related` MIME to embed the official logo via CID.
    - **Single Notification Flow**: Deliver only one high-impact "Footage Ready" email once the upload is complete. Avoid intermediate processing alerts to reduce user inbox clutter.
    - **Retention Notice**: Every email MUST include the disclaimer: *"⚠️ Footage is stored for 5 days from the recording date and will be permanently deleted thereafter."*

### 3.6 Cloud & Network
- **Google Drive Resilience**: Use **60-second network timeouts** to accommodate fluctuating court WiFi.
- **ONVIF Discovery**: Use a "Total Shouting" strategy probing Multicast, Broadcast, and Subnets simultaneously.
- **Centralized Update Pipeline**:
    - **Anonymous Handoff**: Update checks MUST use a public API Key and Developer Folder ID to query APKs without requiring a user Google login.
    - **Diagnostic Tracing**: All update interactions MUST be logged under the `DriveUpdate` tag for rapid troubleshooting of folder permissions.
- **Storage Monitoring**: The system MUST fetch and display real-time Google Drive storage quotas (`usage` vs `limit`) whenever an account is connected. A high-visibility warning (Red color) MUST be displayed if storage usage exceeds **90%**.

## 4. Administrative Tools
- **Unified Session History**:
    - **Management Panes**: Separate technical logs from video playback into dedicated full-screen panes: **Full Activity History** and **Recorded Videos**.
    - **Categorized Tabs**: Filter logs between ALL, RTSP, and INTERNAL sources.
    - **Playback Tab**: Separate playback review from technical logs.
    - **Persistent Progress**: The history list MUST display real-time progress bars for active Conversion or Upload tasks. These indicators MUST be persistent across app restarts by leveraging WorkManager's `Progress` API.
    - **Email Privacy**: All player emails in the history list MUST be masked by default (e.g., `p*******l@gmail.com`). Multiple emails MUST be masked individually. A "Tap to Reveal" interaction MUST be used to show the full address to the administrator.
    - **Local Video Player**: Use a lifecycle-aware `ExoPlayer` dialog with `Uri.fromFile()` and explicit MIME type detection for `.ts` files.
- **Sectioned Management**: The Admin Panel MUST maintain distinct, separated sections for **License Management**, **Application Updates**, and **Cloud & Storage** using expandable **AdminSection** components. This provides a clean "Dropdown" experience for technical settings and prevents UI clutter. Administrative buttons within these sections MUST maintain a standardized minimum height of **56dp** for ergonomic consistency.
- **Auto-Preview Enforcement**: The system MUST automatically trigger the 5-minute Idle Preview upon a successful cold launch on a configured, licensed device. This provides instant dashboard feedback without requiring user interaction.
- **Automatic Update Enforcement**: The system MUST automatically perform a "silent" update check upon every application cold launch and when refreshing settings. If a newer version is detected, a high-visibility notification MUST be displayed within the Admin Panel.
- **Official Portal**: The app must link to the official landing page (https://seenmypickle-landing.web.app/) in the Admin Header and Setup Wizard for centralized support and sales.
- **Maintenance**: Include a manual "Storage Cleanup" trigger and per-session deletion icons (Trash icon) in the History list. Deleting a session must trigger automatic physical file removal.
- **TV Supplementary Experience (PickleView TV)**:
    - **Architecture**: A silent "Listener" module (`:tv`) with App ID `com.pbcam.tv`.
    - **Continuous Sub-Stream**: MUST utilize the camera's **Sub Stream (stream2)** for 24/7 continuous monitoring to optimize bandwidth and TV CPU utilization.
    - **Tablet Presence Heartbeat**: MUST implement active real-time detection using a **10-second Live Heartbeat** from the Tablet to Firebase. The Tablet explicitly forces `isOnline = true` and triggers a data sync (`syncLiveStatusToCloud`) every 10 seconds to overcome emulator/network jitter.
    - **Atomic Status Sync**: The tablet MUST use `updateChildren()` for cloud synchronization to preserve the `isOnline` presence flag. Presence listeners MUST be extracted from the main sync loop to prevent redundant overhead.
    - **Instant Settings Broadcast**: The tablet MUST broadcast settings changes (Court Tag, RTSP URLs) to the cloud immediately to ensure zero-latency TV updates.
    - **Offline Resilience**: MUST display a high-visibility **"TABLET OFFLINE"** alert (zIndex 400+) if the paired tablet is disconnected or powered off. The alert MUST include a **10-second auto-retry countdown** and a focusable **"RETRY NOW"** button.
    - **Sync Visibility**: The TV Admin Panel MUST display the **Last Sync Time** (HH:mm:ss) to provide clear evidence of "live" data connectivity.
    - **Remote-First Interactivity**: ALL TV dashboard controls MUST be focus-aware and navigable via standard D-Pad remote events, utilizing high-contrast pulsing borders for focused states.
    - **Settings Control**: A focusable settings gear MUST be anchored to the **Bottom-Left** corner to allow remote-driven unpairing and re-configuration.
    - **Technical Info Cluster**: The real-time digital clock MUST be anchored to the **Bottom-Right** corner, sitting below match stats to prevent overlap with top-level status banners.
    - **Detached Status Layout**: For professional public display, the status tag (Left) and Court Name (Right) MUST be detached into separate high-contrast bubbles at the top.
    - **Instant Replay Engine**: The TV MUST implement an automated handover from live feed to **Instant Replay** upon match completion, pulling the file directly from the tablet's local HTTP server.
    - **Replay UX Hardening**: 
        - **Transition Animation**: A 2-second "PREPARING REPLAY" overlay with pulsing branding MUST precede all replay playback to signal the feed switch.
        - **Watch Again Prompt**: Upon video completion, a dedicated overlay MUST ask "WATCH AGAIN?" with focus-aware D-Pad controls.
        - **Circular Countdown**: The prompt MUST include a 20-second circular countdown timer that automatically returns to the live monitoring feed if no action is taken.
    - **Resource Hardening (Feed)**: The TV player MUST explicitly stop and release RTSP resources whenever the tablet is detected as offline or during active local replay playback.
    - **Advanced Diagnostics**: The TV app MUST maintain a dedicated **Admin Panel** displaying Paired ID, Firebase Link, Cloud Latency (Last Sync), and active RTSP/Replay URLs.
    - **Multi-Entry Pairing Recovery**: A **"PAIR NEW DEVICE"** action MUST be accessible from both the Admin Panel and the "Tablet Offline" screen to ensure immediate recovery during tablet failure.
    - **Drop Shadows**: ALL UI text overlaid on video (Status, Court, Timer, Emails) MUST use pronounced drop shadows (Offset 4f, 4f / Blur 8f) for legibility.
    - **Privacy Standards**: Player emails MUST be automatically obscured (e.g., `p***l@gmail.com`) when displayed on the public TV interface.
    - **Hybrid Priority Logic**: TV player MUST prioritize `stream2` for monitoring/recording and fallback to `localReplayUrl` (HTTP) or `lastRecordingUrl` (G-Drive) only when the phone is IDLE and live monitoring is inactive.
    - **Branding Standard**: MUST include the official logo and tagline centered at the bottom, and a real-time digital clock in the top-left corner.
    - **Global Screen-On**: MUST apply `FLAG_KEEP_SCREEN_ON` at the `MainActivity` level to ensure the TV never sleeps while the app is active.

### 3.8 Troubleshooting & Error Management
- **RTSP Diagnostic Logic**: Failures during RTSP capture MUST record the **last 500 characters** of the FFmpeg log into the session's error field. Capturing the start of the log (the banner) is prohibited as it provides no diagnostic value for connection or authentication failures.
- **Exit Code Transparency**: ALL technical failures (FFmpeg, CameraX, G-Drive) MUST include the exact system exit code (e.g., Code 1, -1, 401) in the user-facing error message to facilitate rapid troubleshooting.
- **Log Sanitization**: All technical logs displayed to the user MUST have newlines replaced with pipes (` | `) to maintain UI card integrity.
- **Black Screen Recovery**: Check logs for "corrupt decoded frame." Ensure Rescue Mode is enabled in `ConvertWorker`.
- **Admin Recovery**: Swiping back or tapping "Close" will prompt for unsaved changes before allowing a logout.
- **Resource 0x6a Handling**: Ensure assets are in `res/raw` and accessed via direct `R.id`.
