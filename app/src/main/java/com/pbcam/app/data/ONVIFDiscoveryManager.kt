package com.pbcam.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.*
import java.util.*

data class DiscoveredCamera(
    val ip: String,
    val name: String = "ONVIF Camera"
)

object ONVIFDiscoveryManager {
    private const val TAG = "ONVIFDiscovery"
    private const val DISCOVERY_PORT = 3702
    private const val DISCOVERY_TIMEOUT_MS = 5000 
    private const val MULTICAST_ADDRESS = "239.255.255.250"
    private const val BROADCAST_ADDRESS = "255.255.255.255"

    private fun getProbeMessage(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <Envelope xmlns="http://www.w3.org/2003/05/soap-envelope" 
                  xmlns:tds="http://www.onvif.org/ver10/device/wsdl"
                  xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
            <Header>
                <MessageID xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">uuid:${UUID.randomUUID()}</MessageID>
                <To xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">urn:schemas-xmlsoap-org:ws:2004:08:discovery</To>
                <Action xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">http://schemas.xmlsoap.org/ws/2004/08/discovery/Probe</Action>
            </Header>
            <Body>
                <Probe xmlns="http://schemas.xmlsoap.org/ws/2004/08/discovery">
                    <Types>tds:Device</Types>
                    <Scopes/>
                </Probe>
            </Body>
        </Envelope>
    """.trimIndent()

    private fun getLocalWifiIp(context: Context): InetAddress? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networks = cm.allNetworks
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val lp = cm.getLinkProperties(network)
                    val addr = lp?.linkAddresses?.find { it.address is Inet4Address }?.address
                    if (addr != null) return addr
                }
            }
            
            // Fallback to active network if Wi-Fi transport wasn't explicitly found
            val activeNetwork = cm.activeNetwork
            val activeLp = cm.getLinkProperties(activeNetwork)
            activeLp?.linkAddresses?.find { it.address is Inet4Address }?.address
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP", e)
            null
        }
    }

    suspend fun discoverCameras(context: Context, targetIp: String? = null): List<DiscoveredCamera> = withContext(Dispatchers.IO) {
        val discovered = mutableSetOf<String>()
        val cameras = mutableListOf<DiscoveredCamera>()
        
        var socket: DatagramSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("pbcam_discovery_lock").apply {
                setReferenceCounted(true)
                acquire()
            }

            Log.d(TAG, "Starting discovery (Target: ${targetIp ?: "All Vectors"})...")
            
            val localIp = getLocalWifiIp(context)
            socket = if (localIp != null) {
                Log.d(TAG, "Binding socket to: ${localIp.hostAddress}")
                DatagramSocket(InetSocketAddress(localIp, 0))
            } else {
                Log.w(TAG, "Wi-Fi interface not identified, using default socket")
                DatagramSocket()
            }
            
            socket.broadcast = true
            socket.reuseAddress = true
            socket.soTimeout = 1500 
            
            val probeData = getProbeMessage().toByteArray()
            
            if (targetIp != null) {
                // Targeted Unicast Probe (Manual IP)
                Log.d(TAG, "Sending targeted probe to $targetIp")
                val packet = DatagramPacket(probeData, probeData.size, InetAddress.getByName(targetIp), DISCOVERY_PORT)
                socket.send(packet)
            } else {
                // Total Shouting Strategy
                val targets = mutableListOf<String>()
                targets.add(MULTICAST_ADDRESS)
                targets.add(BROADCAST_ADDRESS)
                
                // Common Residential Subnets
                targets.add("192.168.1.255")
                targets.add("192.168.0.255")
                targets.add("10.0.0.255")
                targets.add("10.1.1.255")
                targets.add("10.0.2.255") // Emulator Default NAT Subnet
                
                // Emulator Bridge (Targeting Host machine)
                targets.add("10.0.2.2")

                targets.forEach { target ->
                    try {
                        Log.d(TAG, "Shouting to $target")
                        val packet = DatagramPacket(probeData, probeData.size, InetAddress.getByName(target), DISCOVERY_PORT)
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send to $target: ${e.message}")
                    }
                }
            }

            val startTime = System.currentTimeMillis()
            val listenDuration = if (targetIp != null) 2000L else DISCOVERY_TIMEOUT_MS.toLong()
            
            while (System.currentTimeMillis() - startTime < listenDuration) {
                val buf = ByteArray(16384)
                val responsePacket = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(responsePacket)
                    val ip = responsePacket.address.hostAddress ?: continue
                    Log.d(TAG, "Response received from $ip")
                    
                    // Simple check if it looks like an ONVIF response (contains Envelope or Probe)
                    val responseStr = String(responsePacket.data, 0, responsePacket.length)
                    if (responseStr.contains("Envelope", ignoreCase = true)) {
                        if (discovered.add(ip)) {
                            cameras.add(DiscoveredCamera(ip))
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    if (targetIp != null) break // Quick exit for manual probe
                } catch (e: Exception) {
                    Log.w(TAG, "Socket receive error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discovery critical failure", e)
        } finally {
            socket?.close()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lock release error", e)
            }
        }

        Log.d(TAG, "Discovery cycle finished. Found ${cameras.size} unique devices.")
        cameras
    }
}
