package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoManager
import com.example.data.database.AppDatabase
import com.example.data.models.ContactEntity
import com.example.data.models.ConversationEntity
import com.example.data.models.MessageEntity
import com.example.data.models.PeerEntity
import com.example.data.repository.MessengerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MessengerViewModel(application: Application) : AndroidViewModel(application) {

    private val cryptoManager = CryptoManager(application)
    private val database = AppDatabase.getInstance(application)
    val repository = MessengerRepository(application, cryptoManager, database)

    val conversations: StateFlow<List<ConversationEntity>> = repository.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<ContactEntity>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nearbyPeers: StateFlow<List<PeerEntity>> = repository.allPeers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectedPeersCount: StateFlow<Int> = repository.connectionManager.connectedPeersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val localIp: StateFlow<String?> = repository.localIp
    val serverPort: StateFlow<Int> = repository.serverPort

    val myPeerId: String = cryptoManager.peerId
    val myPublicKeyBase64: String = cryptoManager.publicKeyBase64

    private val _myDisplayName = MutableStateFlow(cryptoManager.getDisplayName())
    val myDisplayName = _myDisplayName.asStateFlow()

    private val _activeConversation = MutableStateFlow<ConversationEntity?>(null)
    val activeConversation = _activeConversation.asStateFlow()

    val activeMessages: StateFlow<List<MessageEntity>> = _activeConversation
        .flatMapLatest { conv ->
            if (conv != null) {
                repository.getMessagesForConversation(conv.peerId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Dialog & Inspection states
    private val _inspectingMessage = MutableStateFlow<MessageEntity?>(null)
    val inspectingMessage = _inspectingMessage.asStateFlow()

    private val _inspectingContact = MutableStateFlow<ContactEntity?>(null)
    val inspectingContact = _inspectingContact.asStateFlow()

    private val _showVerifyDialog = MutableStateFlow(false)
    val showVerifyDialog = _showVerifyDialog.asStateFlow()

    private val _showDirectConnectDialog = MutableStateFlow(false)
    val showDirectConnectDialog = _showDirectConnectDialog.asStateFlow()

    private val _connectionStatusMessage = MutableStateFlow<String?>(null)
    val connectionStatusMessage = _connectionStatusMessage.asStateFlow()

    fun openConversation(conv: ConversationEntity) {
        _activeConversation.value = conv
        repository.markConversationAsRead(conv.peerId)
        // If peer address is known, ensure we are connected
        if (conv.lastAddress.isNotEmpty()) {
            repository.connectToPeer(conv.lastAddress, conv.lastPort, conv.peerId)
        }
    }

    fun closeConversation() {
        _activeConversation.value = null
    }

    fun startChatWithPeer(peer: PeerEntity) {
        viewModelScope.launch {
            val conv = repository.startOrGetConversation(
                peerId = peer.peerId,
                peerName = peer.name,
                peerPublicKey = peer.publicKey,
                address = peer.hostAddress,
                port = peer.port
            )
            repository.connectToPeer(peer.hostAddress, peer.port, peer.peerId)
            openConversation(conv)
        }
    }

    fun sendMessage(text: String) {
        val conv = _activeConversation.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                repository.sendMessage(conv.peerId, text.trim())
            } catch (e: Exception) {
                _connectionStatusMessage.value = "Encryption/Send error: ${e.message}"
            }
        }
    }

    fun connectDirectIp(ip: String, port: Int) {
        _connectionStatusMessage.value = "Connecting to $ip:$port..."
        repository.connectToPeer(ip, port) { success ->
            viewModelScope.launch {
                if (success) {
                    _connectionStatusMessage.value = "Connected to $ip:$port successfully!"
                    _showDirectConnectDialog.value = false
                } else {
                    _connectionStatusMessage.value = "Connection failed. Check IP & port."
                }
            }
        }
    }

    fun setConversationVerified(peerId: String, verified: Boolean) {
        repository.setConversationVerified(peerId, verified)
        val current = _activeConversation.value
        if (current?.peerId == peerId) {
            _activeConversation.value = current.copy(isVerified = verified)
        }
    }

    fun updateDisplayName(name: String) {
        if (name.isNotBlank()) {
            cryptoManager.setDisplayName(name.trim())
            _myDisplayName.value = name.trim()
            repository.refreshNetworkInfo()
        }
    }

    fun refreshNetwork() {
        repository.refreshNetworkInfo()
    }

    fun inspectMessage(message: MessageEntity?) {
        _inspectingMessage.value = message
    }

    fun inspectContact(contact: ContactEntity) {
        _inspectingContact.value = contact
    }

    fun inspectContactByPeerId(peerId: String) {
        viewModelScope.launch {
            val contact = repository.getContact(peerId)
            if (contact != null) {
                _inspectingContact.value = contact
            }
        }
    }

    fun closeContactInspection() {
        _inspectingContact.value = null
    }

    fun updateContactNickname(peerId: String, nickname: String) {
        repository.updateContactNickname(peerId, nickname)
        val current = _inspectingContact.value
        if (current?.peerId == peerId) {
            _inspectingContact.value = current.copy(customNickname = nickname.trim())
        }
    }

    fun updateContactNotes(peerId: String, notes: String) {
        repository.updateContactNotes(peerId, notes)
        val current = _inspectingContact.value
        if (current?.peerId == peerId) {
            _inspectingContact.value = current.copy(notes = notes.trim())
        }
    }

    fun toggleContactFavorite(peerId: String, isFavorite: Boolean) {
        repository.toggleContactFavorite(peerId, isFavorite)
        val current = _inspectingContact.value
        if (current?.peerId == peerId) {
            _inspectingContact.value = current.copy(isFavorite = isFavorite)
        }
    }

    fun toggleContactBlock(peerId: String, isBlocked: Boolean) {
        repository.toggleContactBlock(peerId, isBlocked)
        val current = _inspectingContact.value
        if (current?.peerId == peerId) {
            _inspectingContact.value = current.copy(isBlocked = isBlocked)
        }
    }

    fun startChatWithContact(contact: ContactEntity) {
        viewModelScope.launch {
            val conv = repository.startOrGetConversation(
                peerId = contact.peerId,
                peerName = contact.effectiveName,
                peerPublicKey = contact.publicKey,
                address = contact.lastKnownHost,
                port = contact.lastKnownPort
            )
            if (contact.lastKnownHost.isNotEmpty()) {
                repository.connectToPeer(contact.lastKnownHost, contact.lastKnownPort, contact.peerId)
            }
            openConversation(conv)
            _inspectingContact.value = null
        }
    }

    fun deleteContact(peerId: String) {
        repository.deleteContact(peerId)
        if (_inspectingContact.value?.peerId == peerId) {
            _inspectingContact.value = null
        }
    }

    fun toggleVerifyDialog(show: Boolean) {
        _showVerifyDialog.value = show
    }

    fun toggleDirectConnectDialog(show: Boolean) {
        _showDirectConnectDialog.value = show
    }

    fun clearStatusMessage() {
        _connectionStatusMessage.value = null
    }
}
