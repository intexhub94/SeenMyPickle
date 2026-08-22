# SeeMyPickle: Detailed Changelog (July 26, 2026)

Today's session focused on resolving critical blockers preventing recording/upload and ensuring recording stability. We also significantly enhanced the Session History capabilities with local playback, source categorization, and administrative controls.

## 🛠️ Critical Bug Fixes
*   **Google Drive Upload Fixed**: Discovered and fixed a critical typo in the resumable upload URL.
*   **Samsung Tablet Reliability**: Tagged the `UploadWorker` as **Expedited** (High Priority) to prevent Samsung's battery optimization from killing the background upload process.
*   **Admin Panel Save Fixed**: Resolved an issue where the "SAVE ALL CHANGES" button was incorrectly disabled. Decoupled local UI edits from background persistence.
*   **Google Login Restored**: Fixed a broken Google Integration caused by an incorrect Web Client ID.
*   **RTSP Code 255 Handling**: Fixed a bug where stopping an RTSP recording was reported as a failure.
*   **RTSP Rescue Mode**: Implemented a "Safe Delivery" system for RTSP feeds. If video processing fails or produces black frames, the app automatically skips the watermark and delivers the raw footage.
*   **System Attribution Fix**: Resolved the `attributionTag not declared` errors on Android 12+ by correctly declaring the monitoring purpose in the manifest.
*   **Resource ID 0x6a Robustness**: Optimized resource loading for the email system to use direct Raw resource IDs instead of dynamic lookup. This improves stability on devices where shared libraries might cause resource ID collisions.
*   **Splash Screen Hang Resolved**: Optimized the app startup sequence by moving non-critical initialization tasks (licensing handshakes, session recovery) to background I/O threads.

## 🚀 Performance & Stability
*   **Optimized Processing Pipeline**: 
    *   **Higher Throughput**: Increased video encoding bitrates to 12Mbps for faster hardware write speeds.
    *   **Zero-Latency Encoding**: Tuned FFmpeg to disable B-frames (`-bf 0`) for the watermarking phase, resulting in significantly faster processing.
    *   **Lean Uploads**: Optimized the background worker to update the UI less frequently during uploads, reducing CPU overhead and speeding up delivery.
    *   **Hardened Retries**: TS parts are now only deleted after a *successful* upload. This prevents "No source found" errors during retries.
    *   **Increased Resilience**: Network timeouts for Google Drive and Gmail have been increased to 60 seconds to ensure stability on fluctuating WiFi connections.
*   **Configurable Recording Limits**: Introduced a new setting in the Admin Panel that allows administrators to set a maximum recording duration (1 to 6 hours). The app now automatically stops and uploads the footage once the limit is reached, ensuring storage efficiency and prompt delivery.
*   **Session History Persistence**: Removed auto-deletion logic to ensure all recording sessions are retained permanently in the local database.
*   **UI Architecture Refactor**: Modularized the dashboard by extracting the `AdminPanel` into a standalone, optimized component.
*   **Thermal Intelligence**: Implemented a "Queue Throttler" in the `ConvertWorker`. The app now intelligently pauses video processing if the device temperature exceeds 45°C.
*   **Low-Level Graphics & WebView Cleanup**: Explicitly enabled hardware acceleration and optimized manifest flags to silence vendor-specific graphics warnings.
*   **YouTube Player Removal**: Removed the unreliable YouTube background player from the codebase. The app now strictly uses high-stability direct MP4 sources.

## ✨ New Features
*   **Official Website Integration**: Added a direct link to the SeeMyPickle landing page (https://seemypickle-landing.web.app/) in both the Admin Panel and the Setup Wizard.
*   **Professional Branded Emails**: Completely redesigned the email notification system.
    *   **Official Logo Integration**: The SeeMyPickle logo is now embedded directly in all alert emails.
    *   **5-Day Disclaimer**: Added a rolling retention disclaimer (footage is deleted after 5 days).
    *   **Single Notification Flow**: Streamlined the user experience by moving to a single, high-impact "Footage Ready" email.
*   **Dedicated Recorded Session Viewer**: Separated the "Recorded Sessions" (playback tab) from the general "Session History" (activity logs).
*   **Local Video Playback**: Added a **PLAY** button to completed recordings in the Admin Panel.
*   **Per-Session Deletion**: Added a trash icon to each session log and recording. Admins can now delete specific entries, which automatically cleans up the associated video files.

## 🔑 License Management
*   **License Validity System**: Implemented a comprehensive duration system. Administrators can now set license validity (7 Days, 30 Days, 1 Year, Lifetime, or Manual) in the Keygen app.
*   **Automatic Expiry Enforcement**: The client app now monitors its expiry date in real-time.
*   **Trial Expiry Guidance**: Updated the license lockout system to provide professional guidance for trial users. When a trial expires, the app now explicitly directs users to contact support at iamrenzel26@gmail.com for license renewal.
*   **Manual Resync**: Admins can now force an instant license check with the cloud via the "RESYNC" button in the Admin Panel.
*   **Automated License Recovery**: Implemented a mandatory "Cloud Heartbeat" during activation. Entering an existing valid key now automatically notifies the cloud and restores the "Active" status in the Admin tool.
*   **License Renewal Dialog**: Fixed the "RENEW OR CHANGE LICENSE" button. It now correctly displays a popup dialog allowing admins to input and activate a new license key instantly.

## 🎨 UI & UX Improvements
*   **License Key Auto-Dashing**: Dramatically improved the product activation experience by implementing automatic dash generation in the license key field (`XXXX-XXXX-XXXX-XXXX`).
*   **Buttery Smooth Typing**: Resolved a performance bug in the "Player Email" field. By decoupling the keyboard input from the background persistence logic, typing is now instant and responsive.
*   **Auto-Reset Email Field**: The "Player Email" field is now automatically cleared as soon as a recording is stopped.
*   **Professional Icons**:
    *   **Icon-Only Pause**: Replaced the "PAUSE RECORDING" text with a clean Pause icon.
    *   **Unified Admin Gear**: Replaced the Admin Panel button with a minimalist Gear icon on all devices.
*   **Tablet Orientation Enforcement**: Hardened the rule that forces the application into **Landscape Mode** on all tablet devices.
*   **High-Contrast Light Theme**: Updated the Light Theme palette to use deeper Forest Green (#2E7D32) and Burnt Orange (#D84315).

---
**Current Build Status**: Fully Operational / Verified
**Next Steps**: Project deployment.
