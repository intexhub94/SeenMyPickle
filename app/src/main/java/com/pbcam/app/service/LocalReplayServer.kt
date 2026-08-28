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

    fun startServer(provider: () -> String) {
        statusJsonProvider = provider
        if (!isRunning) {
            isRunning = true
            executor = Executors.newSingleThreadExecutor()
            executor?.execute { runServer() }
        }
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
        } catch (e: Exception) {}
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
                val line = input.readLine() ?: return@execute
                android.util.Log.d(TAG, "Request: $line")
                
                if (line.startsWith("GET /status")) {
                    val json = statusJsonProvider?.invoke() ?: "{\"isOnline\":true}"
                    val output = client.getOutputStream()
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${json.toByteArray().size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                    output.write(json.toByteArray())
                    output.flush()
                    Log.d(TAG, "Served /status JSON")
                } else if (line.startsWith("GET /replay")) {
                    val file = currentFile
                    if (file != null && file.exists()) {
                        val output = client.getOutputStream()
                        val fis = FileInputStream(file)
                        
                        val header = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: video/mp4\r\n" +
                                "Content-Length: ${file.length()}\r\n" +
                                "Accept-Ranges: bytes\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Connection: close\r\n\r\n"
                        
                        output.write(header.toByteArray())
                        
                        val buffer = ByteArray(65536) // 64KB buffer
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                        fis.close()
                        Log.d(TAG, "Served ${file.name} to ${client.inetAddress}")
                    } else {
                        Log.e(TAG, "Replay file missing: ${file?.name ?: "null"}")
                        val error = "HTTP/1.1 404 Not Found\r\n\r\n"
                        client.getOutputStream().write(error.toByteArray())
                    }
                } else {
                    Log.w(TAG, "Forbidden request: $line")
                    val forbidden = "HTTP/1.1 403 Forbidden\r\n\r\n"
                    client.getOutputStream().write(forbidden.toByteArray())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client handling error: ${e.message}")
            } finally {
                try { client.close() } catch (e: Exception) {}
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
