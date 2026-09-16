package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ContactMetadataDialog
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MessengerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MessengerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MessengerApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MessengerApp(viewModel: MessengerViewModel) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val nearbyPeers by viewModel.nearbyPeers.collectAsStateWithLifecycle()
    val connectedPeersCount by viewModel.connectedPeersCount.collectAsStateWithLifecycle()
    val localIp by viewModel.localIp.collectAsStateWithLifecycle()
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()
    val myDisplayName by viewModel.myDisplayName.collectAsStateWithLifecycle()
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val inspectingMessage by viewModel.inspectingMessage.collectAsStateWithLifecycle()
    val inspectingContact by viewModel.inspectingContact.collectAsStateWithLifecycle()
    val showDirectConnectDialog by viewModel.showDirectConnectDialog.collectAsStateWithLifecycle()
    val statusMessage by viewModel.connectionStatusMessage.collectAsStateWithLifecycle()

    // Persistent Contact Metadata Dialog
    inspectingContact?.let { contact ->
        ContactMetadataDialog(
            contact = contact,
            onDismiss = { viewModel.closeContactInspection() },
            onUpdateNickname = { nickname -> viewModel.updateContactNickname(contact.peerId, nickname) },
            onUpdateNotes = { notes -> viewModel.updateContactNotes(contact.peerId, notes) },
            onToggleFavorite = { fav -> viewModel.toggleContactFavorite(contact.peerId, fav) },
            onToggleBlock = { blocked -> viewModel.toggleContactBlock(contact.peerId, blocked) },
            onToggleVerify = { verified -> viewModel.setConversationVerified(contact.peerId, verified) },
            onStartChat = { viewModel.startChatWithContact(contact) }
        )
    }

    val currentConv = activeConversation
    if (currentConv != null) {
        val isPeerOnline = nearbyPeers.any { it.peerId == currentConv.peerId && it.isConnected }
        ChatScreen(
            conversation = currentConv,
            messages = activeMessages,
            inspectingMessage = inspectingMessage,
            isPeerOnline = isPeerOnline,
            onSendMessage = { text -> viewModel.sendMessage(text) },
            onBack = { viewModel.closeConversation() },
            onInspectMessage = { msg -> viewModel.inspectMessage(msg) },
            onVerifyChanged = { verified ->
                viewModel.setConversationVerified(currentConv.peerId, verified)
            },
            onInspectContact = { peerId ->
                viewModel.inspectContactByPeerId(peerId)
            }
        )
    } else {
        MainScreen(
            conversations = conversations,
            contacts = contacts,
            nearbyPeers = nearbyPeers,
            connectedPeersCount = connectedPeersCount,
            localIp = localIp,
            serverPort = serverPort,
            myPeerId = viewModel.myPeerId,
            myPublicKeyBase64 = viewModel.myPublicKeyBase64,
            myDisplayName = myDisplayName,
            statusMessage = statusMessage,
            showDirectConnectDialog = showDirectConnectDialog,
            onOpenConversation = { conv -> viewModel.openConversation(conv) },
            onInspectContact = { contact -> viewModel.inspectContact(contact) },
            onStartChatWithPeer = { peer -> viewModel.startChatWithPeer(peer) },
            onStartChatWithContact = { contact -> viewModel.startChatWithContact(contact) },
            onConnectDirectIp = { ip, port -> viewModel.connectDirectIp(ip, port) },
            onUpdateDisplayName = { name -> viewModel.updateDisplayName(name) },
            onRefreshNetwork = { viewModel.refreshNetwork() },
            onToggleDirectConnect = { show -> viewModel.toggleDirectConnectDialog(show) },
            onClearStatusMessage = { viewModel.clearStatusMessage() }
        )
    }
}
