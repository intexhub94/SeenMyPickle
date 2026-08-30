#!/usr/bin/env python3
"""
SeenMyPickle Desktop Media Server & Storage Vault
------------------------------------------------
A Windows Desktop application with a Tkinter GUI and embedded HTTP Server.
Receives match recordings uploaded from the SeenMyPickle Tablet app, saves them
to a user-selected directory, and serves byte-range media streams to the PickleView TV app.
"""

import os
import sys
import json
import socket
import shutil
import time
import threading
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

# Tkinter GUI Imports
import tkinter as tk
from tkinter import ttk, filedialog, messagebox, scrolledtext

CONFIG_FILE = "config.json"
DEFAULT_PORT = 5000

# Global state for server configuration and runtime
server_instance = None
server_thread = None
is_server_running = False

def get_local_ip():
    """Detects the primary local IP address of the Windows PC on LAN."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0.1)
        s.connect(('10.255.255.255', 1))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def get_free_space_gb(folder_path):
    """Returns free disk space in Gigabytes for the specified folder."""
    try:
        total, used, free = shutil.disk_usage(folder_path)
        return round(free / (1024 ** 3), 2)
    except Exception:
        return 0.0

class MediaRequestHandler(BaseHTTPRequestHandler):
    """HTTP Request Handler providing endpoints for Tablet uploads and TV streaming."""

    def log_message(self, format, *args):
        # Override standard logging to redirect to GUI log console
        msg = f"[{datetime.now().strftime('%H:%M:%S')}] {args[0]} - {args[1]} {args[2]}"
        if hasattr(self.server, 'gui_log'):
            self.server.gui_log(msg)

    def _set_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Range, Filename")

    def do_OPTIONS(self):
        self.send_response(200)
        self._set_cors_headers()
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip('/')

        if path == '/api/status':
            self.handle_status()
        elif path == '/api/recordings':
            self.handle_list_recordings()
        elif path.startswith('/api/stream/'):
            filename = urllib.parse.unquote(path[len('/api/stream/'):])
            self.handle_stream_file(filename)
        else:
            self.send_error(404, "Endpoint not found")

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip('/')

        if path == '/api/upload':
            self.handle_upload()
        else:
            self.send_error(404, "Endpoint not found")

    def do_DELETE(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip('/')

        if path.startswith('/api/recordings/'):
            filename = urllib.parse.unquote(path[len('/api/recordings/'):])
            self.handle_delete(filename)
        else:
            self.send_error(404, "Endpoint not found")

    def handle_status(self):
        storage_path = getattr(self.server, 'storage_path', '')
        free_gb = get_free_space_gb(storage_path) if storage_path else 0.0
        response_data = {
            "status": "running",
            "service": "SeenMyPickle Windows Media Server",
            "ip": get_local_ip(),
            "port": self.server.server_port,
            "storage_path": storage_path,
            "free_storage_gb": free_gb
        }
        data_bytes = json.dumps(response_data).encode('utf-8')
        self.send_response(200)
        self._set_cors_headers()
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data_bytes)))
        self.end_headers()
        self.wfile.write(data_bytes)

    def handle_list_recordings(self):
        storage_path = getattr(self.server, 'storage_path', '')
        recordings = []
        if storage_path and os.path.exists(storage_path):
            for file in os.listdir(storage_path):
                if file.lower().endswith(('.mp4', '.mkv', '.mov')):
                    file_path = os.path.join(storage_path, file)
                    stat = os.stat(file_path)
                    recordings.append({
                        "filename": file,
                        "size_bytes": stat.st_size,
                        "size_mb": round(stat.st_size / (1024 * 1024), 2),
                        "modified_timestamp": stat.st_mtime,
                        "created_date": datetime.fromtimestamp(stat.st_mtime).strftime('%Y-%m-%d %H:%M:%S'),
                        "stream_url": f"http://{get_local_ip()}:{self.server.server_port}/api/stream/{urllib.parse.quote(file)}"
                    })
        recordings.sort(key=lambda x: x["modified_timestamp"], reverse=True)
        data_bytes = json.dumps({"recordings": recordings, "total": len(recordings)}).encode('utf-8')
        self.send_response(200)
        self._set_cors_headers()
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data_bytes)))
        self.end_headers()
        self.wfile.write(data_bytes)

    def handle_upload(self):
        storage_path = getattr(self.server, 'storage_path', '')
        if not storage_path or not os.path.exists(storage_path):
            self.send_error(500, "Storage folder not configured or missing")
            return

        # Extract filename from header or query param or auto-generate
        filename = self.headers.get('Filename')
        if not filename:
            parsed = urllib.parse.urlparse(self.path)
            query = urllib.parse.parse_qs(parsed.query)
            filename = query.get('filename', [f"recording_{int(time.time())}.mp4"])[0]

        filename = os.path.basename(filename) # Sanitize filename
        dest_path = os.path.join(storage_path, filename)
        temp_path = dest_path + ".tmp"

        content_len = int(self.headers.get('Content-Length', 0))
        if hasattr(self.server, 'gui_log'):
            self.server.gui_log(f"📥 Receiving upload: {filename} ({round(content_len / (1024*1024), 2)} MB)")

        try:
            bytes_read = 0
            with open(temp_path, 'wb') as f:
                while bytes_read < content_len:
                    chunk_size = min(64 * 1024, content_len - bytes_read)
                    chunk = self.rfile.read(chunk_size)
                    if not chunk:
                        break
                    f.write(chunk)
                    bytes_read += len(chunk)

            # Atomic move
            if os.path.exists(dest_path):
                os.remove(dest_path)
            os.rename(temp_path, dest_path)

            if hasattr(self.server, 'gui_log'):
                self.server.gui_log(f"✅ Upload completed & saved: {filename}")

            resp = {"status": "success", "message": "File saved", "filename": filename, "bytes_received": bytes_read}
            data_bytes = json.dumps(resp).encode('utf-8')
            self.send_response(200)
            self._set_cors_headers()
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data_bytes)))
            self.end_headers()
            self.wfile.write(data_bytes)

        except Exception as e:
            if os.path.exists(temp_path):
                try: os.remove(temp_path)
                except Exception: pass
            if hasattr(self.server, 'gui_log'):
                self.server.gui_log(f"❌ Upload failed: {str(e)}")
            self.send_error(500, f"Upload error: {str(e)}")

    def handle_stream_file(self, filename):
        storage_path = getattr(self.server, 'storage_path', '')
        file_path = os.path.join(storage_path, os.path.basename(filename))

        if not os.path.exists(file_path):
            self.send_error(404, "Video file not found")
            return

        file_size = os.path.getsize(file_path)
        range_header = self.headers.get('Range', None)

        if range_header:
            # Parse Range header for Media3/ExoPlayer seek support (e.g. Range: bytes=1000-5000)
            try:
                bytes_str = range_header.replace('bytes=', '')
                parts = bytes_str.split('-')
                start = int(parts[0]) if parts[0] else 0
                end = int(parts[1]) if len(parts) > 1 and parts[1] else file_size - 1
                if start >= file_size or end >= file_size or start > end:
                    self.send_error(416, "Requested Range Not Satisfiable")
                    return

                chunk_len = end - start + 1
                self.send_response(206) # Partial Content
                self._set_cors_headers()
                self.send_header("Content-Type", "video/mp4")
                self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
                self.send_header("Content-Length", str(chunk_len))
                self.send_header("Accept-Ranges", "bytes")
                self.end_headers()

                with open(file_path, 'rb') as f:
                    f.seek(start)
                    bytes_sent = 0
                    while bytes_sent < chunk_len:
                        buf = f.read(min(64 * 1024, chunk_len - bytes_sent))
                        if not buf:
                            break
                        self.wfile.write(buf)
                        bytes_sent += len(buf)
            except Exception as e:
                # Connection dropped during seek/play
                pass
        else:
            self.send_response(200)
            self._set_cors_headers()
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Content-Length", str(file_size))
            self.send_header("Accept-Ranges", "bytes")
            self.end_headers()

            with open(file_path, 'rb') as f:
                shutil.copyfileobj(f, self.wfile)

    def handle_delete(self, filename):
        storage_path = getattr(self.server, 'storage_path', '')
        file_path = os.path.join(storage_path, os.path.basename(filename))
        if os.path.exists(file_path):
            try:
                os.remove(file_path)
                resp = json.dumps({"status": "deleted", "filename": filename}).encode('utf-8')
                self.send_response(200)
                self._set_cors_headers()
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(resp)))
                self.end_headers()
                self.wfile.write(resp)
            except Exception as e:
                self.send_error(500, f"Delete failed: {str(e)}")
        else:
            self.send_error(404, "File not found")

class ServerApp(tk.Tk):
    """Tkinter Desktop Application Window."""

    def __init__(self):
        super().__init__()

        self.title("SeenMyPickle - Windows Media Server & Storage Vault")
        self.geometry("780x560")
        self.minsize(700, 480)

        # Style configuration
        self.style = ttk.Style(self)
        self.style.theme_use('clam')

        self.storage_folder = tk.StringVar()
        self.port_var = tk.IntVar(value=DEFAULT_PORT)
        self.server_status = tk.StringVar(value="STOPPED")

        self.load_config()
        self.create_widgets()

    def load_config(self):
        if os.path.exists(CONFIG_FILE):
            try:
                with open(CONFIG_FILE, 'r') as f:
                    cfg = json.load(f)
                    self.storage_folder.set(cfg.get("storage_folder", os.path.expanduser("~/Videos/SeenMyPickle")))
                    self.port_var.set(cfg.get("port", DEFAULT_PORT))
            except Exception:
                self.set_default_folder()
        else:
            self.set_default_folder()

    def set_default_folder(self):
        default_dir = os.path.join(os.path.expanduser("~"), "Videos", "SeenMyPickle")
        self.storage_folder.set(default_dir)

    def save_config(self):
        cfg = {
            "storage_folder": self.storage_folder.get(),
            "port": self.port_var.get()
        }
        try:
            with open(CONFIG_FILE, 'w') as f:
                json.dump(cfg, f, indent=2)
        except Exception as e:
            self.log_gui(f"⚠️ Failed to save config: {e}")

    def create_widgets(self):
        # Header Frame
        header_frame = ttk.Frame(self, padding=15)
        header_frame.pack(fill=tk.X)

        title_lbl = ttk.Label(header_frame, text="🎾 SeenMyPickle Desktop Server", font=("Helvetica", 16, "bold"))
        title_lbl.pack(side=tk.LEFT)

        self.status_badge = tk.Label(
            header_frame, text=" STOPPED ", bg="#D32F2F", fg="white",
            font=("Helvetica", 10, "bold"), padx=10, pady=3
        )
        self.status_badge.pack(side=tk.RIGHT)

        ttk.Separator(self, orient=tk.HORIZONTAL).pack(fill=tk.X, padx=10)

        # Main Settings Container
        settings_frame = ttk.LabelFrame(self, text=" Storage & Network Settings ", padding=15)
        settings_frame.pack(fill=tk.X, padx=15, pady=10)

        # Storage Path Row
        ttk.Label(settings_frame, text="Footage Storage Folder:", font=("Helvetica", 10)).grid(row=0, column=0, sticky=tk.W, pady=5)
        path_entry = ttk.Entry(settings_frame, textvariable=self.storage_folder, width=50)
        path_entry.grid(row=0, column=1, padx=10, pady=5, sticky=tk.EW)

        browse_btn = ttk.Button(settings_frame, text="Browse...", command=self.browse_folder)
        browse_btn.grid(row=0, column=2, padx=5, pady=5)

        # IP and Port Row
        info_frame = ttk.Frame(settings_frame)
        info_frame.grid(row=1, column=0, columnspan=3, sticky=tk.EW, pady=10)

        ip_str = get_local_ip()
        ttk.Label(info_frame, text=f"PC Local IP: ", font=("Helvetica", 10, "bold")).pack(side=tk.LEFT)
        ttk.Label(info_frame, text=f"{ip_str}", font=("Helvetica", 10), foreground="#007ACC").pack(side=tk.LEFT, padx=(0, 20))

        ttk.Label(info_frame, text="Server Port: ", font=("Helvetica", 10, "bold")).pack(side=tk.LEFT)
        port_entry = ttk.Entry(info_frame, textvariable=self.port_var, width=8)
        port_entry.pack(side=tk.LEFT, padx=(0, 20))

        self.free_space_lbl = ttk.Label(info_frame, text="Free Space: Calculating...", font=("Helvetica", 10))
        self.free_space_lbl.pack(side=tk.LEFT)

        settings_frame.columnconfigure(1, weight=1)

        # Controls & Action Buttons Frame
        btn_frame = ttk.Frame(self, padding=5)
        btn_frame.pack(fill=tk.X, padx=15)

        self.start_btn = ttk.Button(btn_frame, text="▶ Start Server", command=self.toggle_server)
        self.start_btn.pack(side=tk.LEFT, padx=5)

        open_folder_btn = ttk.Button(btn_frame, text="📁 Open Storage Folder", command=self.open_storage_folder)
        open_folder_btn.pack(side=tk.LEFT, padx=5)

        # Log Window Frame
        log_frame = ttk.LabelFrame(self, text=" Server Activity Log ", padding=10)
        log_frame.pack(fill=tk.BOTH, expand=True, padx=15, pady=10)

        self.log_text = scrolledtext.ScrolledText(log_frame, wrap=tk.WORD, height=12, font=("Consolas", 9))
        self.log_text.pack(fill=tk.BOTH, expand=True)

        self.update_free_space()
        self.log_gui(f"App initialized. Local IP: {get_local_ip()}")

    def update_free_space(self):
        folder = self.storage_folder.get()
        if folder and os.path.exists(folder):
            free_gb = get_free_space_gb(folder)
            self.free_space_lbl.config(text=f"Free Space: {free_gb} GB")
        else:
            self.free_space_lbl.config(text="Free Space: N/A")

    def browse_folder(self):
        chosen = filedialog.askdirectory(
            title="Select Storage Folder for Recorded Matches",
            initialdir=self.storage_folder.get() or os.path.expanduser("~")
        )
        if chosen:
            self.storage_folder.set(chosen)
            self.save_config()
            self.update_free_space()
            self.log_gui(f"Selected storage folder: {chosen}")

    def open_storage_folder(self):
        folder = self.storage_folder.get()
        if not os.path.exists(folder):
            os.makedirs(folder, exist_ok=True)
        if sys.platform == "win32":
            os.startfile(folder)
        elif sys.platform == "darwin":
            os.system(f'open "{folder}"')
        else:
            os.system(f'xdg-open "{folder}"')

    def log_gui(self, message):
        def _append():
            self.log_text.insert(tk.END, f"[{datetime.now().strftime('%H:%M:%S')}] {message}\n")
            self.log_text.see(tk.END)
        self.after(0, _append)

    def toggle_server(self):
        global is_server_running
        if is_server_running:
            self.stop_server()
        else:
            self.start_server()

    def start_server(self):
        global server_instance, server_thread, is_server_running

        folder = self.storage_folder.get()
        if not folder:
            messagebox.showerror("Error", "Please select a storage folder first.")
            return

        if not os.path.exists(folder):
            try:
                os.makedirs(folder, exist_ok=True)
            except Exception as e:
                messagebox.showerror("Error", f"Could not create storage folder:\n{e}")
                return

        port = self.port_var.get()
        ip = get_local_ip()

        try:
            server_instance = HTTPServer(('0.0.0.0', port), MediaRequestHandler)
            server_instance.storage_path = folder
            server_instance.gui_log = self.log_gui

            server_thread = threading.Thread(target=server_instance.serve_forever, daemon=True)
            server_thread.start()

            is_server_running = True
            self.status_badge.config(text=" RUNNING ", bg="#388E3C")
            self.start_btn.config(text="⏹ Stop Server")
            self.save_config()
            self.update_free_space()

            self.log_gui(f"🚀 Server started successfully!")
            self.log_gui(f"  ➜ Tablet Upload URL: http://{ip}:{port}/api/upload")
            self.log_gui(f"  ➜ TV Replay URL:    http://{ip}:{port}/api/recordings")

        except Exception as e:
            messagebox.showerror("Server Error", f"Failed to start HTTP Server on port {port}:\n{e}")
            self.log_gui(f"❌ Failed to start server: {e}")

    def stop_server(self):
        global server_instance, is_server_running
        if server_instance:
            server_instance.shutdown()
            server_instance.server_close()
            server_instance = None

        is_server_running = False
        self.status_badge.config(text=" STOPPED ", bg="#D32F2F")
        self.start_btn.config(text="▶ Start Server")
        self.log_gui("⏹ Server stopped.")

    def on_closing(self):
        if is_server_running:
            if messagebox.askokcancel("Quit", "Server is currently running. Do you want to stop the server and exit?"):
                self.stop_server()
                self.destroy()
        else:
            self.destroy()

if __name__ == "__main__":
    app = ServerApp()
    app.protocol("WM_DELETE_WINDOW", app.on_closing)
    app.mainloop()
