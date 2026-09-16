package com.example.p2p

import android.util.Log
import com.example.crypto.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface P2PEventListener {
    fun onMessageReceived(packet: P2PPacket, payload: MessagePayload)
    fun onDeliveryAckReceived(messageId: String, senderId: String)
    fun onReadAckReceived(messageId: String, senderId: String)
    fun onPeerConnected(peerId: String, name: String, publicKey: String, ip: String, port: Int)
    fun onPeerDisconnected(peerId: String)
    fun onSyncRequestReceived(peerId: String, sinceTimestamp: Long, responder: (List<MessagePayload>) -> Unit)
    fun onSyncResponseReceived(peerId: String, payloads: List<MessagePayload>)
}

class P2PConnectionManager(
    private val cryptoManager: CryptoManager,
    private val scope: CoroutineScope,
    private val listener: P2PEventListener
) {
    companion object {
        const val DEFAULT_PORT = 8888
        private const val TAG = "P2PConnectionManager"
    }

    var serverPort: Int = DEFAULT_PORT
        private set

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var isRunning = false

    // Active peer connection holders: peerId -> PeerConnection
    private val activeConnections = ConcurrentHashMap<String, PeerConnection>()

    private val _connectedPeersCount = MutableStateFlow(0)
    val connectedPeersCount = _connectedPeersCount.asStateFlow()

    private class PeerConnection(
        val peerId: String,
        val socket: Socket,
        val reader: BufferedReader,
        val writer: BufferedWriter,
        var job: Job? = null
    )

    fun startServer(): Int {
        if (isRunning) return serverPort
        isRunning = true

        // Try standard port first, then fallback to next available ports
        var bound = false
        var portToTry = DEFAULT_PORT
        while (!bound && portToTry < DEFAULT_PORT + 10) {
            try {
                serverSocket = ServerSocket(portToTry)
                serverPort = portToTry
                bound = true
                Log.d(TAG, "P2P Server listening on port $serverPort")
            } catch (e: Exception) {
                portToTry++
            }
        }

        if (!bound) {
            // Allocate ephemeral port if all else fails
            serverSocket = ServerSocket(0)
            serverPort = serverSocket!!.localPort
        }

        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleIncomingSocket(clientSocket) }
                } catch (e: Exception) {
                    if (!isRunning) break
                }
            }
        }

        return serverPort
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null

        activeConnections.values.forEach { connection ->
            connection.job?.cancel()
            try {
                connection.socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }
        activeConnections.clear()
        _connectedPeersCount.value = 0
    }

    private suspend fun handleIncomingSocket(socket: Socket) = withContext(Dispatchers.IO) {
        var remotePeerId: String? = null
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            // Send immediate HELLO greeting to the connecting peer
            val hello = P2PPacket(
                type = PacketType.HELLO,
                id = UUID.randomUUID().toString(),
                senderId = cryptoManager.peerId,
                senderName = cryptoManager.getDisplayName(),
                senderPublicKey = cryptoManager.publicKeyBase64,
                recipientId = "",
                timestamp = System.currentTimeMillis()
            )
            writer.write(hello.toJson() + "\n")
            writer.flush()

            var line: String? = null
            while (socket.isConnected && !socket.isClosed && reader.readLine().also { line = it } != null) {
                val jsonLine = line?.trim() ?: continue
                if (jsonLine.isEmpty()) continue

                val packet = P2PPacket.fromJson(jsonLine) ?: continue
                remotePeerId = packet.senderId

                // Register connection if not already registered
                if (!activeConnections.containsKey(packet.senderId)) {
                    val conn = PeerConnection(packet.senderId, socket, reader, writer)
                    activeConnections[packet.senderId] = conn
                    _connectedPeersCount.value = activeConnections.size
                    listener.onPeerConnected(
                        peerId = packet.senderId,
                        name = packet.senderName,
                        publicKey = packet.senderPublicKey,
                        ip = socket.inetAddress.hostAddress ?: "",
                        port = socket.port
                    )
                }

                processIncomingPacket(packet, writer)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Socket closed or error: ${e.message}")
        } finally {
            remotePeerId?.let { id ->
                activeConnections.remove(id)
                _connectedPeersCount.value = activeConnections.size
                listener.onPeerDisconnected(id)
            }
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun processIncomingPacket(packet: P2PPacket, writer: BufferedWriter) {
        when (packet.type) {
            PacketType.HELLO -> {
                listener.onPeerConnected(
                    peerId = packet.senderId,
                    name = packet.senderName,
                    publicKey = packet.senderPublicKey,
                    ip = "",
                    port = 0
                )
            }
            PacketType.MESSAGE -> {
                val payload = MessagePayload.fromJson(packet.payload)
                if (payload != null) {
                    listener.onMessageReceived(packet, payload)
                    // Auto-reply with delivery ACK immediately
                    sendDeliveryAck(packet.senderId, payload.messageId)
                }
            }
            PacketType.ACK -> {
                val messageId = packet.payload
                listener.onDeliveryAckReceived(messageId, packet.senderId)
            }
            PacketType.READ -> {
                val messageId = packet.payload
                listener.onReadAckReceived(messageId, packet.senderId)
            }
            PacketType.SYNC_REQ -> {
                val since = packet.payload.toLongOrNull() ?: 0L
                listener.onSyncRequestReceived(packet.senderId, since) { payloads ->
                    val array = JSONArray()
                    payloads.forEach { array.put(JSONObject(it.toJson())) }
                    val responsePacket = P2PPacket(
                        type = PacketType.SYNC_RES,
                        id = UUID.randomUUID().toString(),
                        senderId = cryptoManager.peerId,
                        senderName = cryptoManager.getDisplayName(),
                        senderPublicKey = cryptoManager.publicKeyBase64,
                        recipientId = packet.senderId,
                        timestamp = System.currentTimeMillis(),
                        payload = array.toString()
                    )
                    try {
                        writer.write(responsePacket.toJson() + "\n")
                        writer.flush()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error responding to SYNC_REQ: ${e.message}")
                    }
                }
            }
            PacketType.SYNC_RES -> {
                try {
                    val array = JSONArray(packet.payload)
                    val payloads = mutableListOf<MessagePayload>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        MessagePayload.fromJson(obj.toString())?.let { payloads.add(it) }
                    }
                    listener.onSyncResponseReceived(packet.senderId, payloads)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing SYNC_RES: ${e.message}")
                }
            }
            PacketType.PING -> {
                val pong = P2PPacket(
                    type = PacketType.PONG,
                    id = packet.id,
                    senderId = cryptoManager.peerId,
                    senderName = cryptoManager.getDisplayName(),
                    senderPublicKey = cryptoManager.publicKeyBase64,
                    recipientId = packet.senderId,
                    timestamp = System.currentTimeMillis()
                )
                try {
                    writer.write(pong.toJson() + "\n")
                    writer.flush()
                } catch (e: Exception) {
                    // ignore
                }
            }
            else -> {}
        }
    }

    /**
     * Connects directly to a peer by host address and port
     */
    fun connectToPeer(host: String, port: Int, peerIdHint: String? = null, onConnected: ((Boolean) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), 4000)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

                // Send HELLO packet with our identity & public key
                val hello = P2PPacket(
                    type = PacketType.HELLO,
                    id = UUID.randomUUID().toString(),
                    senderId = cryptoManager.peerId,
                    senderName = cryptoManager.getDisplayName(),
                    senderPublicKey = cryptoManager.publicKeyBase64,
                    recipientId = peerIdHint ?: "",
                    timestamp = System.currentTimeMillis()
                )
                writer.write(hello.toJson() + "\n")
                writer.flush()

                // Read peer's response
                val responseLine = reader.readLine()
                if (responseLine != null) {
                    val responsePacket = P2PPacket.fromJson(responseLine)
                    if (responsePacket != null) {
                        val peerId = responsePacket.senderId
                        val conn = PeerConnection(peerId, socket, reader, writer)
                        activeConnections[peerId] = conn
                        _connectedPeersCount.value = activeConnections.size

                        listener.onPeerConnected(
                            peerId = peerId,
                            name = responsePacket.senderName,
                            publicKey = responsePacket.senderPublicKey,
                            ip = host,
                            port = port
                        )

                        onConnected?.invoke(true)

                        // Start reading messages from this peer in background
                        conn.job = launch {
                            try {
                                var line: String? = null
                                while (socket.isConnected && !socket.isClosed && reader.readLine().also { line = it } != null) {
                                    val pkt = line?.trim()?.takeIf { it.isNotEmpty() }?.let { P2PPacket.fromJson(it) } ?: continue
                                    processIncomingPacket(pkt, writer)
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Connection lost to $peerId: ${e.message}")
                            } finally {
                                activeConnections.remove(peerId)
                                _connectedPeersCount.value = activeConnections.size
                                listener.onPeerDisconnected(peerId)
                            }
                        }
                        return@launch
                    }
                }
                onConnected?.invoke(false)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to $host:$port - ${e.message}")
                onConnected?.invoke(false)
                try {
                    socket?.close()
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }
    }

    /**
     * Send encrypted message packet to peer
     */
    suspend fun sendMessage(
        recipientId: String,
        recipientHost: String,
        recipientPort: Int,
        messagePayload: MessagePayload
    ): Boolean = withContext(Dispatchers.IO) {
        val packet = P2PPacket(
            type = PacketType.MESSAGE,
            id = UUID.randomUUID().toString(),
            senderId = cryptoManager.peerId,
            senderName = cryptoManager.getDisplayName(),
            senderPublicKey = cryptoManager.publicKeyBase64,
            recipientId = recipientId,
            timestamp = System.currentTimeMillis(),
            payload = messagePayload.toJson()
        )

        return@withContext sendPacket(recipientId, recipientHost, recipientPort, packet)
    }

    fun sendDeliveryAck(recipientId: String, messageId: String) {
        scope.launch(Dispatchers.IO) {
            val conn = activeConnections[recipientId]
            if (conn != null) {
                val packet = P2PPacket(
                    type = PacketType.ACK,
                    id = UUID.randomUUID().toString(),
                    senderId = cryptoManager.peerId,
                    senderName = cryptoManager.getDisplayName(),
                    senderPublicKey = cryptoManager.publicKeyBase64,
                    recipientId = recipientId,
                    timestamp = System.currentTimeMillis(),
                    payload = messageId
                )
                try {
                    conn.writer.write(packet.toJson() + "\n")
                    conn.writer.flush()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send delivery ack: ${e.message}")
                }
            }
        }
    }

    fun sendReadAck(recipientId: String, recipientHost: String, recipientPort: Int, messageId: String) {
        scope.launch(Dispatchers.IO) {
            val packet = P2PPacket(
                type = PacketType.READ,
                id = UUID.randomUUID().toString(),
                senderId = cryptoManager.peerId,
                senderName = cryptoManager.getDisplayName(),
                senderPublicKey = cryptoManager.publicKeyBase64,
                recipientId = recipientId,
                timestamp = System.currentTimeMillis(),
                payload = messageId
            )
            sendPacket(recipientId, recipientHost, recipientPort, packet)
        }
    }

    fun requestSync(peerId: String, host: String, port: Int, sinceTimestamp: Long) {
        scope.launch(Dispatchers.IO) {
            val packet = P2PPacket(
                type = PacketType.SYNC_REQ,
                id = UUID.randomUUID().toString(),
                senderId = cryptoManager.peerId,
                senderName = cryptoManager.getDisplayName(),
                senderPublicKey = cryptoManager.publicKeyBase64,
                recipientId = peerId,
                timestamp = System.currentTimeMillis(),
                payload = sinceTimestamp.toString()
            )
            sendPacket(peerId, host, port, packet)
        }
    }

    private fun sendPacket(recipientId: String, host: String, port: Int, packet: P2PPacket): Boolean {
        // First try existing open connection
        val existingConn = activeConnections[recipientId]
        if (existingConn != null && existingConn.socket.isConnected && !existingConn.socket.isClosed) {
            try {
                existingConn.writer.write(packet.toJson() + "\n")
                existingConn.writer.flush()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing to active connection: ${e.message}")
                activeConnections.remove(recipientId)
            }
        }

        // If no active connection and valid host/port, open a direct socket
        if (host.isNotEmpty() && port > 0) {
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(host, port), 3000)
                    val w = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8))
                    w.write(packet.toJson() + "\n")
                    w.flush()
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct socket send to $host:$port failed: ${e.message}")
            }
        }

        return false
    }

    fun isPeerConnected(peerId: String): Boolean {
        val conn = activeConnections[peerId]
        return conn != null && conn.socket.isConnected && !conn.socket.isClosed
    }
}
