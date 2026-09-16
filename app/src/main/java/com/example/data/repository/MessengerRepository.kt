package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.crypto.CryptoManager
import com.example.crypto.EncryptedMessagePayload
import com.example.data.database.AppDatabase
import com.example.data.models.ContactEntity
import com.example.data.models.ConversationEntity
import com.example.data.models.MessageEntity
import com.example.data.models.MessageStatus
import com.example.data.models.PeerEntity
import com.example.p2p.DiscoveredPeer
import com.example.p2p.MessagePayload
import com.example.p2p.NetworkUtils
import com.example.p2p.P2PConnectionManager
import com.example.p2p.P2PDiscovery
import com.example.p2p.P2PEventListener
import com.example.p2p.P2PPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MessengerRepository(
    private val context: Context,
    val cryptoManager: CryptoManager,
    private val database: AppDatabase
) : P2PEventListener {

    companion object {
        private const val TAG = "MessengerRepository"
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageDao = database.messageDao()
    private val conversationDao = database.conversationDao()
    private val peerDao = database.peerDao()
    private val contactDao = database.contactDao()

    val allConversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()
    val allPeers: Flow<List<PeerEntity>> = peerDao.getAllPeers()
    val allContacts: Flow<List<ContactEntity>> = contactDao.getAllContacts()

    private fun formatFingerprint(hex: String): String {
        return hex.chunked(2).take(16).joinToString(":")
    }

    private suspend fun updateOrCreateContact(
        peerId: String,
        displayName: String,
        publicKey: String,
        host: String = "",
        port: Int = 8888,
        timestamp: Long = System.currentTimeMillis()
    ): ContactEntity {
        val existing = contactDao.getContact(peerId)
        val fingerprint = if (existing != null && existing.keyFingerprint.isNotEmpty()) {
            existing.keyFingerprint
        } else {
            formatFingerprint(cryptoManager.computeFingerprint(publicKey))
        }
        val safetyNumber = if (existing != null && existing.safetyNumber.isNotEmpty()) {
            existing.safetyNumber
        } else {
            cryptoManager.computeSafetyNumber(publicKey)
        }

        val contact = if (existing != null) {
            existing.copy(
                displayName = displayName.ifEmpty { existing.displayName },
                lastKnownHost = host.ifEmpty { existing.lastKnownHost },
                lastKnownPort = if (port > 0) port else existing.lastKnownPort,
                lastSeenTimestamp = timestamp,
                publicKey = publicKey.ifEmpty { existing.publicKey },
                safetyNumber = safetyNumber
            )
        } else {
            ContactEntity(
                peerId = peerId,
                displayName = displayName,
                customNickname = "",
                publicKey = publicKey,
                keyFingerprint = fingerprint,
                safetyNumber = safetyNumber,
                isVerified = false,
                verifiedTimestamp = null,
                notes = "",
                lastKnownHost = host,
                lastKnownPort = port,
                firstSeenTimestamp = timestamp,
                lastSeenTimestamp = timestamp,
                isFavorite = false,
                isBlocked = false
            )
        }
        contactDao.insertOrUpdate(contact)
        return contact
    }

    private val _localIp = MutableStateFlow<String?>(null)
    val localIp = _localIp.asStateFlow()

    private val _serverPort = MutableStateFlow(8888)
    val serverPort = _serverPort.asStateFlow()

    lateinit var connectionManager: P2PConnectionManager
        private set

    private lateinit var discovery: P2PDiscovery

    init {
        initNetworking()
    }

    private fun initNetworking() {
        connectionManager = P2PConnectionManager(
            cryptoManager = cryptoManager,
            scope = repositoryScope,
            listener = this
        )
        val port = connectionManager.startServer()
        _serverPort.value = port

        _localIp.value = NetworkUtils.getLocalIpAddress()

        discovery = P2PDiscovery(
            context = context,
            cryptoManager = cryptoManager,
            serverPort = port
        ) { discoveredPeer ->
            handlePeerDiscovered(discoveredPeer)
        }
        discovery.start(repositoryScope)

        // Reset stale connection states
        repositoryScope.launch {
            peerDao.resetAllConnections()
        }
    }

    fun refreshNetworkInfo() {
        _localIp.value = NetworkUtils.getLocalIpAddress()
        discovery.announceNow(repositoryScope)
    }

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    private fun handlePeerDiscovered(discovered: DiscoveredPeer) {
        repositoryScope.launch {
            val peer = PeerEntity(
                peerId = discovered.peerId,
                name = discovered.name,
                hostAddress = discovered.ipAddress,
                port = discovered.port,
                publicKey = discovered.publicKey,
                lastSeen = discovered.timestamp,
                isConnected = connectionManager.isPeerConnected(discovered.peerId)
            )
            peerDao.insertOrUpdatePeer(peer)

            // Persist contact metadata in Room database
            updateOrCreateContact(
                peerId = discovered.peerId,
                displayName = discovered.name,
                publicKey = discovered.publicKey,
                host = discovered.ipAddress,
                port = discovered.port,
                timestamp = discovered.timestamp
            )

            // Also update known conversation address if exists
            val conv = conversationDao.getConversation(discovered.peerId)
            if (conv != null) {
                conversationDao.updatePeerAddress(discovered.peerId, discovered.ipAddress, discovered.port)
            }
        }
    }

    fun connectToPeer(host: String, port: Int, peerIdHint: String? = null, onResult: ((Boolean) -> Unit)? = null) {
        connectionManager.connectToPeer(host, port, peerIdHint, onResult)
    }

    suspend fun startOrGetConversation(
        peerId: String,
        peerName: String,
        peerPublicKey: String,
        address: String = "",
        port: Int = 8888
    ): ConversationEntity {
        // Ensure contact metadata exists in Room
        updateOrCreateContact(
            peerId = peerId,
            displayName = peerName,
            publicKey = peerPublicKey,
            host = address,
            port = port
        )

        var conv = conversationDao.getConversation(peerId)
        if (conv == null) {
            val safetyNumber = cryptoManager.computeSafetyNumber(peerPublicKey)
            conv = ConversationEntity(
                peerId = peerId,
                peerName = peerName,
                peerPublicKey = peerPublicKey,
                lastAddress = address,
                lastPort = port,
                lastMessageText = "End-to-End Encrypted conversation started",
                lastMessageTimestamp = System.currentTimeMillis(),
                unreadCount = 0,
                isVerified = false,
                safetyNumber = safetyNumber
            )
            conversationDao.insertOrUpdate(conv)
        }
        return conv
    }

    /**
     * Send end-to-end encrypted message
     */
    suspend fun sendMessage(conversationId: String, text: String): MessageEntity {
        val conversation = conversationDao.getConversation(conversationId)
            ?: throw IllegalStateException("Conversation not found for ID: $conversationId")

        val peerPublicKey = conversation.peerPublicKey
        // 1. Encrypt text with AES-256-GCM + ECDH, and sign with ECDSA
        val encrypted: EncryptedMessagePayload = cryptoManager.encrypt(text, peerPublicKey)

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val message = MessageEntity(
            id = messageId,
            conversationId = conversationId,
            senderId = cryptoManager.peerId,
            senderName = cryptoManager.getDisplayName(),
            recipientId = conversationId,
            decryptedText = text,
            ciphertext = encrypted.ciphertext,
            iv = encrypted.iv,
            signature = encrypted.signature,
            senderPublicKey = cryptoManager.publicKeyBase64,
            timestamp = timestamp,
            status = MessageStatus.PENDING,
            isOutgoing = true,
            isSignatureVerified = true
        )

        // Save locally to Room DB immediately
        messageDao.insertMessage(message)

        // Update conversation preview
        val updatedConv = conversation.copy(
            lastMessageText = text,
            lastMessageTimestamp = timestamp
        )
        conversationDao.insertOrUpdate(updatedConv)

        // 2. Transmit through P2P Socket
        repositoryScope.launch {
            val payload = MessagePayload(
                messageId = messageId,
                conversationId = conversationId,
                ciphertext = encrypted.ciphertext,
                iv = encrypted.iv,
                signature = encrypted.signature,
                timestamp = timestamp
            )

            val success = connectionManager.sendMessage(
                recipientId = conversationId,
                recipientHost = conversation.lastAddress,
                recipientPort = conversation.lastPort,
                messagePayload = payload
            )

            if (success) {
                messageDao.updateMessageStatus(messageId, MessageStatus.SENT)
            }
        }

        return message
    }

    fun markConversationAsRead(conversationId: String) {
        repositoryScope.launch {
            conversationDao.clearUnreadCount(conversationId)
            messageDao.markIncomingMessagesAsRead(conversationId)

            val conv = conversationDao.getConversation(conversationId)
            if (conv != null) {
                // Send read receipts to peer for unread messages
                val recent = messageDao.getRecentMessages(conversationId)
                recent.filter { !it.isOutgoing && it.status != MessageStatus.READ }.forEach { msg ->
                    connectionManager.sendReadAck(conversationId, conv.lastAddress, conv.lastPort, msg.id)
                }
            }
        }
    }

    fun setConversationVerified(peerId: String, verified: Boolean) {
        repositoryScope.launch {
            conversationDao.updateVerification(peerId, verified)
            val now = if (verified) System.currentTimeMillis() else null
            contactDao.updateVerification(peerId, verified, now)
        }
    }

    // --- P2PEventListener implementations ---

    override fun onMessageReceived(packet: P2PPacket, payload: MessagePayload) {
        repositoryScope.launch {
            try {
                // Check if sender is blocked
                val contact = contactDao.getContact(packet.senderId)
                if (contact?.isBlocked == true) {
                    Log.d(TAG, "Ignoring incoming message from blocked contact: ${packet.senderId}")
                    return@launch
                }

                // Update contact metadata
                updateOrCreateContact(
                    peerId = packet.senderId,
                    displayName = packet.senderName,
                    publicKey = packet.senderPublicKey,
                    timestamp = payload.timestamp
                )

                // Decrypt end-to-end encrypted payload using sender's public key
                val result = cryptoManager.decrypt(
                    ciphertextBase64 = payload.ciphertext,
                    ivBase64 = payload.iv,
                    signatureBase64 = payload.signature,
                    senderPublicKeyBase64 = packet.senderPublicKey
                )

                val message = MessageEntity(
                    id = payload.messageId,
                    conversationId = packet.senderId,
                    senderId = packet.senderId,
                    senderName = packet.senderName,
                    recipientId = cryptoManager.peerId,
                    decryptedText = result.plaintext,
                    ciphertext = payload.ciphertext,
                    iv = payload.iv,
                    signature = payload.signature,
                    senderPublicKey = packet.senderPublicKey,
                    timestamp = payload.timestamp,
                    status = MessageStatus.DELIVERED,
                    isOutgoing = false,
                    isSignatureVerified = result.isSignatureValid
                )

                messageDao.insertMessage(message)

                // Update or create conversation
                val existingConv = conversationDao.getConversation(packet.senderId)
                val safetyNumber = existingConv?.safetyNumber
                    ?: cryptoManager.computeSafetyNumber(packet.senderPublicKey)

                val newConv = ConversationEntity(
                    peerId = packet.senderId,
                    peerName = packet.senderName,
                    peerPublicKey = packet.senderPublicKey,
                    lastAddress = existingConv?.lastAddress ?: "",
                    lastPort = existingConv?.lastPort ?: 8888,
                    lastMessageText = result.plaintext,
                    lastMessageTimestamp = payload.timestamp,
                    unreadCount = (existingConv?.unreadCount ?: 0) + 1,
                    isVerified = existingConv?.isVerified ?: false,
                    safetyNumber = safetyNumber
                )
                conversationDao.insertOrUpdate(newConv)
            } catch (e: Exception) {
                Log.e(TAG, "Error decrypting incoming message: ${e.message}")
            }
        }
    }

    override fun onDeliveryAckReceived(messageId: String, senderId: String) {
        repositoryScope.launch {
            messageDao.updateMessageStatus(messageId, MessageStatus.DELIVERED)
        }
    }

    override fun onReadAckReceived(messageId: String, senderId: String) {
        repositoryScope.launch {
            messageDao.updateMessageStatus(messageId, MessageStatus.READ)
        }
    }

    override fun onPeerConnected(peerId: String, name: String, publicKey: String, ip: String, port: Int) {
        repositoryScope.launch {
            val peer = PeerEntity(
                peerId = peerId,
                name = name,
                hostAddress = ip,
                port = port,
                publicKey = publicKey,
                lastSeen = System.currentTimeMillis(),
                isConnected = true
            )
            peerDao.insertOrUpdatePeer(peer)

            // Persist contact metadata in Room database
            updateOrCreateContact(
                peerId = peerId,
                displayName = name,
                publicKey = publicKey,
                host = ip,
                port = port,
                timestamp = System.currentTimeMillis()
            )

            // If we have a conversation with this peer, update address and trigger real-time sync
            val conv = conversationDao.getConversation(peerId)
            if (conv != null) {
                if (ip.isNotEmpty()) {
                    conversationDao.updatePeerAddress(peerId, ip, port)
                }
                // Request missing messages since our last known timestamp
                connectionManager.requestSync(peerId, ip, port, conv.lastMessageTimestamp)
            }

            // Retry any pending messages
            val pendingMessages = messageDao.getPendingMessages()
            pendingMessages.filter { it.recipientId == peerId }.forEach { msg ->
                val payload = MessagePayload(
                    messageId = msg.id,
                    conversationId = msg.conversationId,
                    ciphertext = msg.ciphertext,
                    iv = msg.iv,
                    signature = msg.signature,
                    timestamp = msg.timestamp
                )
                val success = connectionManager.sendMessage(peerId, ip, port, payload)
                if (success) {
                    messageDao.updateMessageStatus(msg.id, MessageStatus.SENT)
                }
            }
        }
    }

    override fun onPeerDisconnected(peerId: String) {
        repositoryScope.launch {
            peerDao.updateConnectionStatus(peerId, false)
        }
    }

    override fun onSyncRequestReceived(
        peerId: String,
        sinceTimestamp: Long,
        responder: (List<MessagePayload>) -> Unit
    ) {
        repositoryScope.launch {
            val messages = messageDao.getRecentMessages(peerId)
            val filtered = messages.filter { it.timestamp > sinceTimestamp }
            val payloads = filtered.map {
                MessagePayload(
                    messageId = it.id,
                    conversationId = it.conversationId,
                    ciphertext = it.ciphertext,
                    iv = it.iv,
                    signature = it.signature,
                    timestamp = it.timestamp
                )
            }
            responder(payloads)
        }
    }

    override fun onSyncResponseReceived(peerId: String, payloads: List<MessagePayload>) {
        repositoryScope.launch {
            payloads.forEach { payload ->
                try {
                    val peer = peerDao.getPeer(peerId) ?: return@forEach
                    val result = cryptoManager.decrypt(
                        ciphertextBase64 = payload.ciphertext,
                        ivBase64 = payload.iv,
                        signatureBase64 = payload.signature,
                        senderPublicKeyBase64 = peer.publicKey
                    )
                    val message = MessageEntity(
                        id = payload.messageId,
                        conversationId = peerId,
                        senderId = peerId,
                        senderName = peer.name,
                        recipientId = cryptoManager.peerId,
                        decryptedText = result.plaintext,
                        ciphertext = payload.ciphertext,
                        iv = payload.iv,
                        signature = payload.signature,
                        senderPublicKey = peer.publicKey,
                        timestamp = payload.timestamp,
                        status = MessageStatus.DELIVERED,
                        isOutgoing = false,
                        isSignatureVerified = result.isSignatureValid
                    )
                    messageDao.insertMessage(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync message error: ${e.message}")
                }
            }
        }
    }

    fun updateContactNickname(peerId: String, nickname: String) {
        repositoryScope.launch {
            contactDao.updateNickname(peerId, nickname.trim())
        }
    }

    fun updateContactNotes(peerId: String, notes: String) {
        repositoryScope.launch {
            contactDao.updateNotes(peerId, notes.trim())
        }
    }

    fun toggleContactFavorite(peerId: String, isFavorite: Boolean) {
        repositoryScope.launch {
            contactDao.updateFavorite(peerId, isFavorite)
        }
    }

    fun toggleContactBlock(peerId: String, isBlocked: Boolean) {
        repositoryScope.launch {
            contactDao.updateBlocked(peerId, isBlocked)
        }
    }

    suspend fun getContact(peerId: String): ContactEntity? {
        return contactDao.getContact(peerId)
    }

    fun getContactFlow(peerId: String): Flow<ContactEntity?> {
        return contactDao.getContactFlow(peerId)
    }

    fun deleteContact(peerId: String) {
        repositoryScope.launch {
            contactDao.deleteContact(peerId)
        }
    }
}
