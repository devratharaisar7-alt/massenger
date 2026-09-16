package com.example.p2p

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.crypto.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

data class DiscoveredPeer(
    val peerId: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val publicKey: String,
    val timestamp: Long = System.currentTimeMillis()
)

class P2PDiscovery(
    private val context: Context,
    private val cryptoManager: CryptoManager,
    private val serverPort: Int,
    private val onPeerDiscovered: (DiscoveredPeer) -> Unit
) {
    companion object {
        const val DISCOVERY_PORT = 8889
        private const val TAG = "P2PDiscovery"
    }

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var socket: DatagramSocket? = null
    private var isRunning = false

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("SecureMessengerDiscovery")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
        }

        // Start listening for UDP broadcasts
        listenJob = scope.launch(Dispatchers.IO) {
            runListener()
        }

        // Start periodic broadcasting
        broadcastJob = scope.launch(Dispatchers.IO) {
            runBroadcaster()
        }
    }

    fun stop() {
        isRunning = false
        broadcastJob?.cancel()
        listenJob?.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
        socket = null

        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            // ignore
        }
        multicastLock = null
    }

    fun announceNow(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            sendBroadcastPacket()
        }
    }

    private suspend fun runListener() {
        var listenSocket: DatagramSocket? = null
        try {
            listenSocket = DatagramSocket(DISCOVERY_PORT).apply {
                broadcast = true
                reuseAddress = true
            }
            socket = listenSocket
            val buffer = ByteArray(4096)

            while (isRunning) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    listenSocket.receive(packet)
                    val jsonStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val json = JSONObject(jsonStr)

                    val type = json.optString("type")
                    if (type == "DISCOVER_ANNOUNCE") {
                        val senderPeerId = json.getString("peerId")
                        // Ignore our own broadcast
                        if (senderPeerId != cryptoManager.peerId) {
                            val senderName = json.getString("name")
                            val senderPort = json.getInt("port")
                            val senderPubKey = json.getString("publicKey")
                            val senderIp = packet.address.hostAddress ?: ""

                            if (senderIp.isNotEmpty()) {
                                onPeerDiscovered(
                                    DiscoveredPeer(
                                        peerId = senderPeerId,
                                        name = senderName,
                                        ipAddress = senderIp,
                                        port = senderPort,
                                        publicKey = senderPubKey
                                    )
                                )
                            }
                        }
                    }
                } catch (e: SocketException) {
                    if (!isRunning) break
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing discovery packet: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind discovery listen socket on port $DISCOVERY_PORT: ${e.message}")
        } finally {
            listenSocket?.close()
        }
    }

    private suspend fun runBroadcaster() {
        while (isRunning) {
            sendBroadcastPacket()
            delay(3500) // Announce every 3.5 seconds
        }
    }

    private fun sendBroadcastPacket() {
        try {
            val announcement = JSONObject().apply {
                put("type", "DISCOVER_ANNOUNCE")
                put("peerId", cryptoManager.peerId)
                put("name", cryptoManager.getDisplayName())
                put("port", serverPort)
                put("publicKey", cryptoManager.publicKeyBase64)
                put("timestamp", System.currentTimeMillis())
            }

            val data = announcement.toString().toByteArray(Charsets.UTF_8)
            val broadcastTargets = NetworkUtils.getBroadcastAddresses()

            DatagramSocket().use { ds ->
                ds.broadcast = true
                for (target in broadcastTargets) {
                    try {
                        val packet = DatagramPacket(data, data.size, target, DISCOVERY_PORT)
                        ds.send(packet)
                    } catch (e: Exception) {
                        // ignore per-target errors
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Broadcast send failed: ${e.message}")
        }
    }
}
