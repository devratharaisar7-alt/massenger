package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.models.ContactEntity
import com.example.data.models.ConversationEntity
import com.example.data.models.MessageEntity
import com.example.data.models.MessageStatus
import com.example.data.models.PeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE status = :status ORDER BY timestamp ASC")
    suspend fun getPendingMessages(status: MessageStatus = MessageStatus.PENDING): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :newStatus WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, newStatus: MessageStatus)

    @Query("UPDATE messages SET status = :newStatus WHERE conversationId = :conversationId AND isOutgoing = 0 AND status != 'READ'")
    suspend fun markIncomingMessagesAsRead(conversationId: String, newStatus: MessageStatus = MessageStatus.READ)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteConversationMessages(conversationId: String)
}

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE peerId = :peerId LIMIT 1")
    suspend fun getConversation(peerId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(conversation: ConversationEntity)

    @Query("UPDATE conversations SET isVerified = :isVerified WHERE peerId = :peerId")
    suspend fun updateVerification(peerId: String, isVerified: Boolean)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE peerId = :peerId")
    suspend fun clearUnreadCount(peerId: String)

    @Query("UPDATE conversations SET lastAddress = :address, lastPort = :port WHERE peerId = :peerId")
    suspend fun updatePeerAddress(peerId: String, address: String, port: Int)

    @Query("DELETE FROM conversations WHERE peerId = :peerId")
    suspend fun deleteConversation(peerId: String)
}

@Dao
interface PeerDao {

    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE peerId = :peerId LIMIT 1")
    suspend fun getPeer(peerId: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePeer(peer: PeerEntity)

    @Query("UPDATE peers SET isConnected = :connected WHERE peerId = :peerId")
    suspend fun updateConnectionStatus(peerId: String, connected: Boolean)

    @Query("UPDATE peers SET isConnected = 0")
    suspend fun resetAllConnections()

    @Query("DELETE FROM peers WHERE lastSeen < :staleTime")
    suspend fun deleteStalePeers(staleTime: Long)
}

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY isFavorite DESC, displayName ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 ORDER BY displayName ASC")
    fun getFavoriteContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE peerId = :peerId LIMIT 1")
    suspend fun getContact(peerId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE peerId = :peerId LIMIT 1")
    fun getContactFlow(peerId: String): Flow<ContactEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(contact: ContactEntity)

    @Query("UPDATE contacts SET customNickname = :nickname WHERE peerId = :peerId")
    suspend fun updateNickname(peerId: String, nickname: String)

    @Query("UPDATE contacts SET notes = :notes WHERE peerId = :peerId")
    suspend fun updateNotes(peerId: String, notes: String)

    @Query("UPDATE contacts SET isVerified = :isVerified, verifiedTimestamp = :verifiedTimestamp WHERE peerId = :peerId")
    suspend fun updateVerification(peerId: String, isVerified: Boolean, verifiedTimestamp: Long?)

    @Query("UPDATE contacts SET isFavorite = :isFavorite WHERE peerId = :peerId")
    suspend fun updateFavorite(peerId: String, isFavorite: Boolean)

    @Query("UPDATE contacts SET isBlocked = :isBlocked WHERE peerId = :peerId")
    suspend fun updateBlocked(peerId: String, isBlocked: Boolean)

    @Query("UPDATE contacts SET lastSeenTimestamp = :timestamp, lastKnownHost = :host, lastKnownPort = :port WHERE peerId = :peerId")
    suspend fun updateLastSeen(peerId: String, timestamp: Long, host: String, port: Int)

    @Query("DELETE FROM contacts WHERE peerId = :peerId")
    suspend fun deleteContact(peerId: String)
}
