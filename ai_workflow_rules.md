# SeenMyPickle: AI Governance & Workflow Rules

This document defines the binding rules for all AI-assisted development on the PBCam project. These rules ensure the stability of the "Golden Build" and maintain a rigorous audit trail.

## 1. Mandatory Pre-Implementation Phase
- **Implementation Plan**: Before fixing any bug or adding any new feature, a detailed `implementation_plan.artifact.md` (or equivalent) MUST be created.
- **Content Requirements**:
    - Detailed technical approach for the requested change.
    - **Impact Analysis**: Specific assessment of how the change affects the current "Golden Build" (e.g., UI tweaks vs. background service stability).
    - **Cross-Component Regression**: Explicitly document how the change will be verified against other components (e.g., ensuring a phone fix doesn't break tablets).
    - **Context Audit**: Explicit statement on whether the current AI session context is sufficient to proceed safely.
- **Approval**: No code changes may be applied until the user explicitly approves the implementation plan.

## 2. Bug Tracking & Documentation
- **Bug Fix History**: A dedicated file `bug_fixes_history.md` must be maintained.
- **Complete Explanation Requirement**: Every bug fix MUST be accompanied by a complete and detailed explanation of the root cause (technical "Why"), the specific resolution (technical "How"), and an assessment of why this fix is the most stable path for the Golden Build. This explanation must be present in the `bug_fixes_history.md` entry and summarized in the final `walkthrough.artifact.md`.
- **Entry Format**: Each fix must include:
    - **Date**: The date the issue was addressed.
    - **Issue Description**: Details of the bug as noticed by the user.
    - **Resolution**: How the bug was fixed.
    - **Session ID/Note**: A note on whether the AI context was sufficient for the fix.

## 3. Post-Implementation & Verification
- **User Testing**: After implementation, the user will perform manual testing.
- **Confirmation Loop**: Once the user confirms "Yes, it is working," the AI MUST ask: *"Would you like to update the Code Bible and Code Map to reflect these changes?"*
- **Regression Audit**: Perform a comprehensive codebase audit after approval to ensure no side effects were introduced.

## 4. Continuous Context Awareness
- **Master Files**: The AI MUST always check the following files before any task:
    1. `code_bible.md`
    2. `code_map.md`
    3. `bug_fixes_history.md` (and any other bug reports)
    4. `ai_workflow_rules.md` (this file)

## 5. Security & Performance
- **Security First**: Never relax Firebase Security Rules without documented justification.
- **Golden Build Rule**: Always run `app:assembleDebug` after changes.
- **Extreme Observability**: No background task may run without a corresponding UI indicator (Header Progress Bar).
- **Native-First Preview**: Avoid manual Matrix math for full-screen effects; prioritize native `Zoom/Fill` scaling components.
- **Dual-Stream RTSP**: Always separate high-quality recording traffic from low-latency preview traffic.

## 6. Context Guardrail & Continuity Protocol
- **Insufficient Context Flag**: If at any point the AI determines that the provided context (logs, files, or user descriptions) is insufficient to guarantee a "Golden Build" fix, it MUST:
    1.  Immediately halt the implementation plan.
    2.  Explicitly state: **"STOP: Insufficient Context for Safe Implementation."**
    3.  Provide a **"Gap Analysis"**: List exactly what is missing (e.g., "Missing `CloudClients.kt` source," "Logcat output for tag `DriveUpdate`," "Layout inspector screenshot").
- **User Continuity Path**: The AI MUST provide a specific command or list of files for the user to provide to bridge the gap, allowing the user to "continue the work" by providing the necessary technical eyes or data.
- **Assumptive Fix Prohibition**: The AI is strictly prohibited from guessing or using "best effort" logic when system-critical components (Security, Recording, Billing) are involved.
