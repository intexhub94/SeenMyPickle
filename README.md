# SeenMyPickle (PBCam): 🎾

![SeenMyPickle Logo](app/src/main/res/raw/app_logo_email.png)

**SeenMyPickle** is an industrial-strength, professional Android & Desktop software suite engineered for high-fidelity pickleball court monitoring, automated video recording, local network storage offloading, and public TV streaming.

It features a multi-source video capture engine (RTSP/Internal/USB), a hardware-accelerated watermarking pipeline, a secure hardware-locked licensing framework, a standalone **Windows Desktop Storage Vault Server**, and a dedicated **PickleView TV App** for 24/7 public court displays and instant replays.

---

## 🌟 Key System Modules & Features

### 📹 1. Professional Tablet Recording Engine (`:app`)
- **Multi-Source Support**: High-fidelity video capture from RTSP (CCTV/IP cameras), Internal tablet cameras (via CameraX), and external USB cameras (`LENS_FACING_EXTERNAL`).
- **Dual-Stream Strategy**: High-resolution Main Stream for recording and low-latency Sub-Stream for live dashboard monitoring.
- **Resilient FFmpeg Capture**: 50MB jitter buffers, packet reordering, and TCP transport (`-rtsp_flags prefer_tcp`) to handle wireless camera fluctuations.
- **Micro-Segmenting**: Automatically rotates long matches into 10-minute segments to prevent catastrophic data loss.
- **Jitter-Proof Processing**: Mandatory `fps=30` motion smoothing and `setpts` timestamp normalization during hardware-accelerated MediaCodec conversion.
- **Immersive Continuity**: Captures "Frozen Frame" snapshots to maintain court background visibility during pause and standby states.
- **Adaptive Ergonomics**: Dynamic device DPI & screen metrics (`rememberDeviceScreenMetrics`) scaling UI typography, controls, and cards seamlessly across phones, landscape tablets, and 4K TVs.

### 🖥️ 2. Windows/Linux Desktop Media Server & Storage Vault (`seenmypickle_server.py`)
- **Desktop Graphical Interface (Tkinter)**: Native GUI allows court operators to pick any local drive or external hard folder (`D:\SeenMyPickle_Vault`) with a single click.
- **Fail-Safe Tablet Storage Offload**: Tablet uploads converted `.mp4` recordings over LAN to the Windows PC server (`ServerUploadWorker`) and purges tablet flash memory **ONLY AFTER receiving confirmed HTTP 200 OK receipt**.
- **Byte-Range HTTP Media Server**: Serves fast `HTTP 206 Partial Content` video streams over local Wi-Fi for ExoPlayer seeking on TV devices.
- **Storage & Server Dashboard**: Displays local PC IP address, active port (`5000`), free disk space, and real-time upload/streaming activity logs.

### 🚀 3. Automated Cloud & Email Pipeline
- **Google Drive Integration**: Resumable cloud uploads with configurable rolling retention policies (1 to 30 days auto-purge).
- **Branded Email Alerts**: Sends match download links to up to 5 player emails per match via Gmail API.
- **Privacy Enforcement**: Automated clearing of player email chips and sensitive fields post-match.

### 📺 4. PickleView TV App (`:tv`)
- **24/7 Public Display**: Dedicated Android TV module for court displays.
- **Hybrid Sync Engine**: Sub-10ms Local LAN HTTP status probing combined with Firebase Realtime Database cloud fallback.
- **Tablet Presence Watchdog**: 10-second active heartbeat monitoring with automatic "Tablet Offline" status popups and visible pairing IDs (`PB-XXXX-XXXX`).
- **Instant Replays**: Direct video playback from the Windows PC Desktop Vault or Cloud.

### 🛡️ 5. Security & Administration
- **Hardware-Locked Licensing**: Unique device fingerprints (`ANDROID_ID`) mapped to `PB-XXXX-XXXX` license keys with real-time remote revocation.
- **Admin Security**: On-screen custom numeric keypad for PIN entry (side-by-side landscape layout) and emergency master recovery PIN (**2026**).
- **3-Strike Lockout**: Mandatory 60-second persistent lockout after 3 invalid PIN attempts.

---

## 🏗️ System Architecture

```
+--------------------------+                         +-----------------------------------+
|  Camera Feed             |                         |  Windows PC Storage Vault         |
|  (RTSP / Internal / USB) |                         |  (seenmypickle_server.py)         |
+--------------------------+                         |  [Tkinter GUI + HTTP Media Server]|
             |                                       +-----------------------------------+
             v                                                         ^
+--------------------------+        1. Upload .mp4 over LAN            |
|  SeenMyPickle Tablet     | ------------------------------------------+
|  (:app)                  |        2. Delete local file on 200 OK
|  - CameraX & FFmpeg      | 
|  - MediaCodec Transcode  |                         +-----------------------------------+
|  - WorkManager Pipeline  | ----------------------> | Google Drive & Gmail API (Backup) |
+--------------------------+                         +-----------------------------------+
             |                                                         ^
             | 3. Sub-10ms LAN Probe / Firebase RTDB                   | 4. HTTP Byte-Range Stream
             v                                                         v
+----------------------------------------------------------------------------------------+
|  PickleView TV App (:tv)                                                               |
|  - 24/7 Court Monitoring & Instant Replay Player (ExoPlayer / Media3)                  |
+----------------------------------------------------------------------------------------+
```

---

## 🔌 Desktop Server REST API Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/status` | Returns server health, local IP, port, selected storage directory, and free storage GB. |
| `GET` | `/api/recordings` | Returns JSON catalog of all recorded `.mp4` files with size, timestamps, and stream URLs. |
| `POST` | `/api/upload` | Receives multipart/binary video uploads from the tablet and saves them atomically. |
| `GET` | `/api/stream/{filename}` | Serves HTTP byte-range video streams (`HTTP 206 Partial Content`) for Media3/ExoPlayer seeking. |
| `DELETE` | `/api/recordings/{filename}` | Permanently deletes a recording from the desktop vault. |

---

## 🛠️ Technology Stack

| Component | Technology / Library |
| :--- | :--- |
| **Languages** | Kotlin 1.9+, Python 3 (Tkinter + HTTP Server) |
| **Android UI** | Jetpack Compose (Material 3) |
| **Video Engine** | CameraX, FFmpeg (`ffmpeg-kit`), Media3 (ExoPlayer) |
| **Background Processing**| WorkManager (CoroutineWorkers) |
| **Database & Storage** | Room SQLite, SharedPreferences (`SettingsStore`) |
| **Desktop Server** | Python 3, `tkinter`, `http.server`, `threading`, `json` |
| **Cloud Services** | Firebase Realtime Database, Google Drive API, Gmail API |

---

## 🚦 Getting Started

### 1. Tablet Setup (`:app`)
1. Launch the app on your Android tablet or phone (Android 9.0+).
2. Enter your 16-character SeenMyPickle license key (`PB-XXXX-XXXX`).
3. Use the Setup Wizard to configure your camera source (Auto-Detect available for RTSP IP cameras).

### 2. Running the Windows/Linux PC Media Server
1. Ensure Python 3 is installed on your desktop PC.
2. Launch the server script:
   * **Windows**:
     ```cmd
     python seenmypickle_server.py
     ```
   * **Linux**:
     ```bash
     sudo apt install python3-tk -y
     python3 seenmypickle_server.py
     ```
3. In the GUI, click **Browse...** to pick your storage folder (e.g., `D:\SeenMyPickle_Storage`).
4. Click **▶ Start Server** and note the local IP address (e.g. `192.168.1.105`).
5. Open the **Tablet Admin Panel** -> **Cloud & Storage** -> enable **Offload Footage to Windows PC**, enter the PC IP, tap **Test PC Server Connection**, and save changes.

### 3. PickleView TV Setup (`:tv`)
1. Deploy `:tv` module to your Android TV or Fire TV stick.
2. Enter the **Pairing ID** displayed on the tablet's header (`PB-XXXX-XXXX`).
3. The TV app will automatically detect live match status and stream instant replays directly from the local Windows PC Desktop Vault!

---

## 🛠️ Building & Pushing Updates

### Build Verification Commands
```bash
./gradlew app:assembleDebug
./gradlew tv:assembleDebug
```

### Pushing Commits via Android Studio
1. In Android Studio's top menu bar, click **Git** -> **Push...** (or press `Ctrl + Shift + K`).
2. Select your commits and click **Push**.

---

## ⚖️ License & Support

*Footage is stored and auto-purged based on your configured retention policies. Ensure court players are informed that matches are recorded for monitoring and replay purposes.*

**Developed by SeenMyPickle Smart Court Systems &copy; 2026. All rights reserved.**
