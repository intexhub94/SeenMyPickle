# SeenMyPickle: Golden Build Save Point (Aug 2026)

This document anchors the exact state of the project to ensure 100% context continuity for future development sessions.

## 1. Project Identity & Rebrand (COMPLETED)
- **Brand**: Officially transformed from "SeeMyPickle" to **SeenMyPickle**.
- **Assets**: All icons, splash screens, and email logos migrated from `E:\res` and verified.
- **Resources**: `strings.xml` and `colors.xml` synchronized; critical Google OAuth and Notification keys restored.

## 2. Hardened Architecture (COMPLETED)
- **UI Architecture**: 
    - **Single Hub RTSP**: Top banner is hidden during RTSP recordings. All info (Match Live, Quality Notice, Timer) is unified in the central card.
    - **High-Contrast Header**: Enforced Offset 3f/Blur 6f shadows for all overlaid text.
    - **Auto-Preview**: Dashboard automatically resumes 5-min monitoring after waiver agreement.
- **Recording Engine**:
    - **Stable Bitrate**: Capped at 6Mbps for mid-range hardware resilience.
    - **Stutter-Free Capture**: Uses `-use_wallclock_as_timestamps 1` and `-rtsp_flags prefer_tcp`.
    - **Professional Output**: Forced **Constant Frame Rate (CFR) 30fps** and `yuv420p` pixel format for global device compatibility.
    - **Naming Engine**: Descriptive files using `[masked_email]_[date].mp4` synchronized across the pipeline.
- **System Stability**: 
    - Indefinite `PARTIAL_WAKE_LOCK` acquired during service lifecycle.
    - Dynamic `FLAG_KEEP_SCREEN_ON` enforced during active matches.

## 3. Pending Implementation: Automated Dual-Stream RTSP
- **Goal**: Add a "Sub-Stream" field for preview while using the "Main-Stream" for capture.
- **Automated Scanning**: Implement auto-fill for both streams (using `/stream1` and `/stream2` patterns) in both **Setup Wizard** and **Admin Panel**.
- **Impact Audit**: Verified **PASS**. No regressions found in recording fidelity or background workers.

## 4. Master Documents Status
- [x] **Code Bible**: Fully updated with rebranding, visibility, and performance standards.
- [x] **Code Map**: Fully synchronized with the current system architecture.
- [x] **Bug Fix History**: Logged all refinements from Aug 2-4, 2026.

---
**Continuity Note**: To resume from this point, simply ask the AI to: *"Load the SeenMyPickle Save Point and proceed with the Automated Dual-Stream implementation."*
