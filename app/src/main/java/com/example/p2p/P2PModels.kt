package com.example.p2p

import org.json.JSONObject

enum class PacketType {
    ANNOUNCE,
    HELLO,
    MESSAGE,
    ACK,
    READ,
    SYNC_REQ,
    SYNC_RES,
    PING,
    PONG
}

data class P2PPacket(
    val type: PacketType,
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderPublicKey: String,
    val recipientId: String,
    val timestamp: Long,
    val payload: String = ""
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type.name)
        json.put("id", id)
        json.put("senderId", senderId)
        json.put("senderName", senderName)
        json.put("senderPublicKey", senderPublicKey)
        json.put("recipientId", recipientId)
        json.put("timestamp", timestamp)
        json.put("payload", payload)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): P2PPacket? {
            return try {
                val json = JSONObject(jsonStr)
                P2PPacket(
                    type = PacketType.valueOf(json.getString("type")),
                    id = json.getString("id"),
                    senderId = json.getString("senderId"),
                    senderName = json.getString("senderName"),
                    senderPublicKey = json.getString("senderPublicKey"),
                    recipientId = json.getString("recipientId"),
                    timestamp = json.getLong("timestamp"),
                    payload = json.optString("payload", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class MessagePayload(
    val messageId: String,
    val conversationId: String,
    val ciphertext: String,
    val iv: String,
    val signature: String,
    val timestamp: Long
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("messageId", messageId)
        json.put("conversationId", conversationId)
        json.put("ciphertext", ciphertext)
        json.put("iv", iv)
        json.put("signature", signature)
        json.put("timestamp", timestamp)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): MessagePayload? {
            return try {
                val json = JSONObject(jsonStr)
                MessagePayload(
                    messageId = json.getString("messageId"),
                    conversationId = json.getString("conversationId"),
                    ciphertext = json.getString("ciphertext"),
                    iv = json.getString("iv"),
                    signature = json.getString("signature"),
                    timestamp = json.getLong("timestamp")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
