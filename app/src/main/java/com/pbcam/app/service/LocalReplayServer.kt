package com.pbcam.app.service

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object LocalReplayServer {
    private const val TAG = "LocalReplayServer"
    private const val PORT = 8080
    
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private var currentFile: File? = null
    private var statusJsonProvider: (() -> String)? = null
    private var isRunning = false
    private var appContext: android.content.Context? = null

    fun startServer(context: android.content.Context, provider: () -> String) {
        appContext = context.applicationContext
        statusJsonProvider = provider
        if (!isRunning) {
            isRunning = true
            executor = Executors.newSingleThreadExecutor()
            executor?.execute { runServer() }
        }
    }

    private fun resolveRequestedFile(requestPath: String): File? {
        if (requestPath.contains("id=")) {
            val idStr = requestPath.substringAfter("id=").substringBefore("&")
            val id = idStr.toLongOrNull()
            if (id != null) {
                val repository = try { com.pbcam.app.PBCamApplication.instance.recordingRepository } catch (_: Exception) { null }
                val session = repository?.let { repo ->
                    kotlinx.coroutines.runBlocking { repo.getSession(id) }
                }
                if (session != null) {
                    val f = File(session.filename)
                    if (f.exists() && f.length() > 0) return f
                }
            }
        }
        return currentFile
    }

    fun start(file: File) {
        currentFile = file
        if (!isRunning) {
            isRunning = true
            executor = Executors.newSingleThreadExecutor()
            executor?.execute { runServer() }
        }
    }

    fun setReplayFile(file: File?) {
        currentFile = file
    }

    private fun runServer() {
        try {
            // HARDENING: Enable address reuse to fix EADDRINUSE during quick restarts.
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(PORT))
            }
            Log.d(TAG, "Server started on port $PORT")
            
            while (isRunning) {
                val client = serverSocket?.accept() ?: break
                handleClient(client)
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        } finally {
            stop()
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        
        executor?.shutdownNow()
        executor = null
        currentFile = null
        Log.d(TAG, "Server stopped")
    }

    private fun handleClient(client: Socket) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val input = client.getInputStream().bufferedReader()
                val requestLine = input.readLine() ?: return@execute
                Log.d(TAG, "Request: $requestLine")

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val hLine = input.readLine() ?: break
                    if (hLine.isBlank()) break
                    val parts = hLine.split(":", limit = 2)
                    if (parts.size == 2) {
                        headers[parts[0].trim().lowercase()] = parts[1].trim()
                    }
                }

                val method = requestLine.split(" ").getOrNull(0)?.uppercase() ?: "GET"
                val path = requestLine.split(" ").getOrNull(1) ?: "/"

                if (path.startsWith("/status")) {
                    val json = statusJsonProvider?.invoke() ?: "{\"isOnline\":true}"
                    val jsonBytes = json.toByteArray(Charsets.UTF_8)
                    val output = client.getOutputStream()
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Content-Length: ${jsonBytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                    if (method == "GET") {
                        output.write(jsonBytes)
                    }
                    output.flush()
                    Log.d(TAG, "Served /status JSON")
                } else if (path.startsWith("/replay")) {
                    val file = resolveRequestedFile(path)
                    if (file != null && file.exists() && file.length() > 0) {
                        val fileLength = file.length()
                        val rangeHeader = headers["range"]
                        
                        var startByte = 0L
                        var endByte = fileLength - 1L
                        var isRange = false

                        if (!rangeHeader.isNullOrEmpty() && rangeHeader.startsWith("bytes=")) {
                            val rangeVal = rangeHeader.substringAfter("bytes=").trim()
                            val parts = rangeVal.split("-")
                            startByte = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                            endByte = parts.getOrNull(1)?.toLongOrNull() ?: (fileLength - 1L)
                            if (endByte >= fileLength) endByte = fileLength - 1L
                            if (startByte in 0L..endByte) {
                                isRange = true
                            }
                        }

                        val output = client.getOutputStream()
                        if (isRange) {
                            val contentLength = endByte - startByte + 1L
                            val header = "HTTP/1.1 206 Partial Content\r\n" +
                                    "Content-Type: video/mp4\r\n" +
                                    "Content-Length: $contentLength\r\n" +
                                    "Content-Range: bytes $startByte-$endByte/$fileLength\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Connection: close\r\n\r\n"
                            output.write(header.toByteArray())

                            if (method == "GET") {
                                val fis = FileInputStream(file)
                                fis.skip(startByte)
                                val buffer = ByteArray(65536)
                                var bytesRemaining = contentLength
                                while (bytesRemaining > 0) {
                                    val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                                    val read = fis.read(buffer, 0, toRead)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    bytesRemaining -= read
                                }
                                fis.close()
                            }
                            output.flush()
                            Log.d(TAG, "Served 206 Partial Content range $startByte-$endByte/$fileLength for ${file.name}")
                        } else {
                            val header = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: video/mp4\r\n" +
                                    "Content-Length: $fileLength\r\n" +
                                    "Accept-Ranges: bytes\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Connection: close\r\n\r\n"
                            output.write(header.toByteArray())

                            if (method == "GET") {
                                val fis = FileInputStream(file)
                                val buffer = ByteArray(65536)
                                var read: Int
                                while (fis.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                }
                                fis.close()
                            }
                            output.flush()
                            Log.d(TAG, "Served 200 OK full file ${file.name}")
                        }
                    } else {
                        Log.e(TAG, "Replay file missing for request: $requestLine")
                        val error = "HTTP/1.1 404 Not Found\r\n\r\n"
                        client.getOutputStream().write(error.toByteArray())
                    }
                } else {
                    Log.w(TAG, "Forbidden request: $requestLine")
                    val forbidden = "HTTP/1.1 403 Forbidden\r\n\r\n"
                    client.getOutputStream().write(forbidden.toByteArray())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client handling error: ${e.message}")
            } finally {
                try { client.close() } catch (_: Exception) {}
            }
        }
    }

    fun getLocalUrl(context: android.content.Context): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$PORT/replay"
    }

    fun getLocalIpAddress(): String? {
        try {
            val en = java.net.NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Could not get local IP", ex)
        }
        return null
    }
}
