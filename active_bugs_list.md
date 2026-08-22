# SeenMyPickle: Active Bugs & Task List

This file tracks all currently identified bugs, UI/UX issues, and pending optimizations.
Status: 🔴 Open | 🟡 In-Progress | ✅ Resolved

---

## 📅 August 21, 2026

### 1. 🔐 Admin Passcode Encryption Restoration
- **Description**: Passcode storage is currently set to "Plain-Text" for emergency recovery.
- **Priority**: High
- **Status**: 🔴 Open
- **Note**: Need to restore XOR obfuscation once user confirms the "2026" master code and personal code setting is working reliably.

### 2. 📱 Tablet: Multi-Player Email Chips Persistence
- **Description**: Ensure that if the app is minimized during match setup, the added player email chips persist.
- **Priority**: Medium
- **Status**: 🔴 Open

### 3. ⚙️ Recording Engine: Segments Stability Audit
- **Description**: Perform a stress test for 1-hour recordings to ensure the new session-wide timer doesn't drift from the actual clock.
- **Priority**: High
- **Status**: 🔴 Open

---

## ✅ Recently Resolved (To be moved to `bug_fixes_history.md`)

- [x] **Video Jittering**: Restored capture buffers and implemented `fps=30` smoothing filter.
- [x] **FFmpeg Command Fix**: Replaced 'stimeout' with 'timeout' to fix initialization crash.
- [x] **RTSP Data Failure**: Simplified capture command and implemented Fatal Error Guard.
- [x] **Multi-Email Limit (5)**: Increased capacity and fixed combination logic.
- [x] **RTSP Log Persistence**: Fixed "Code -1" error by preserving final session data.
- [x] **RTSP Data Failure**: Hardened capture command and added connection-handshake diagnostics.
- [x] **TV-Tablet Presence**: Implemented 10s Heartbeat and visible Pairing ID.
- [x] **Google Integration State**: Fixed UI not reflecting "Linked" status after sign-in.
- [x] **Setup Wizard Activation**: Fixed race condition where Firebase sync reverted licensed state to false.
- [x] **Video Corruption & Playback**: Hardened pipeline with FastStart and proper re-muxing.
- [x] **TV App Black Screen**: Fixed by enforcing Strict TCP and adding diagnostic logging.
- [x] **TV Replay 404 Error**: Fixed by hardening `LocalReplayServer` and sync logic.
- [x] **TV Replay UI**: Added loading animations and "Watch Again?" prompts.
- [x] **TV Monitoring: Aspect Ratio Coverage**: Verified `RESIZE_MODE_ZOOM` provides full coverage during black screen fix.
- [x] **Recording Limit Bug**: Enforced session-wide 30-minute timer.
- [x] **TV Black Screen**: Fixed by optimizing player lifecycle and adding URL sanitization.
- [x] **Admin Mapping Shift**: Fixed shifted parameters in `saveAdminSettings`.
- [x] **Admin Lockout**: Implemented "2026" Master Unlock.

---
*Add new bugs below this line using the standard format.*
