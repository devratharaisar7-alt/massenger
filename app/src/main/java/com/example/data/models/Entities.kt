package com.example.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageStatus {
    PENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId"]), Index(value = ["timestamp"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String,
    val decryptedText: String,
    val ciphertext: String,
    val iv: String,
    val signature: String,
    val senderPublicKey: String,
    val timestamp: Long,
    val status: MessageStatus,
    val isOutgoing: Boolean,
    val isSignatureVerified: Boolean = true
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val peerId: String,
    val peerName: String,
    val peerPublicKey: String,
    val lastAddress: String = "",
    val lastPort: Int = 8888,
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val isVerified: Boolean = false,
    val safetyNumber: String = ""
)

@Entity(
    tableName = "contacts",
    indices = [Index(value = ["peerId"], unique = true), Index(value = ["isFavorite"])]
)
data class ContactEntity(
    @PrimaryKey val peerId: String,
    val displayName: String,
    val customNickname: String = "",
    val publicKey: String,
    val keyFingerprint: String,
    val safetyNumber: String,
    val isVerified: Boolean = false,
    val verifiedTimestamp: Long? = null,
    val notes: String = "",
    val lastKnownHost: String = "",
    val lastKnownPort: Int = 8888,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false
) {
    val effectiveName: String
        get() = if (customNickname.isNotBlank()) customNickname else displayName
}

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val peerId: String,
    val name: String,
    val hostAddress: String,
    val port: Int,
    val publicKey: String,
    val lastSeen: Long,
    val isConnected: Boolean = false
)
