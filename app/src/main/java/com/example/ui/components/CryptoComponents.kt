package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.MessageEntity
import com.example.data.models.MessageStatus
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CyberIndigo
import com.example.ui.theme.CyberIndigoDark
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldVerified

/**
 * Visual Identicon matrix canvas generated deterministically from a public key hash
 */
@Composable
fun KeyIdenticonMatrix(
    keyHash: String,
    modifier: Modifier = Modifier,
    size: Int = 80
) {
    val matrixSize = 7
    val safeHash = if (keyHash.length < 32) keyHash.padEnd(32, 'A') else keyHash

    Canvas(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        val cellSize = this.size.width / matrixSize
        val baseColor = CyberIndigo

        for (row in 0 until matrixSize) {
            for (col in 0 until (matrixSize + 1) / 2) {
                val index = (row * matrixSize + col) % safeHash.length
                val charCode = safeHash[index].code
                val isFilled = (charCode % 2) == 0

                if (isFilled) {
                    val mirrorCol = matrixSize - 1 - col
                    val cellColor = if ((charCode % 3) == 0) ElectricCyan else baseColor

                    drawRect(
                        color = cellColor,
                        topLeft = Offset(col * cellSize, row * cellSize),
                        size = Size(cellSize * 0.9f, cellSize * 0.9f)
                    )
                    if (mirrorCol != col) {
                        drawRect(
                            color = cellColor,
                            topLeft = Offset(mirrorCol * cellSize, row * cellSize),
                            size = Size(cellSize * 0.9f, cellSize * 0.9f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pill badge indicating End-to-End Encryption status
 */
@Composable
fun E2EEShieldBadge(
    isVerified: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val bg = if (isVerified) EmeraldContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant
    val iconColor = if (isVerified) EmeraldVerified else CyberIndigo
    val text = if (isVerified) "E2EE Verified" else "E2EE Protected"

    Surface(
        color = bg,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag("e2ee_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isVerified) Icons.Default.VerifiedUser else Icons.Default.Lock,
                contentDescription = text,
                tint = iconColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (isVerified) EmeraldVerified else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Message delivery status ticks
 */
@Composable
fun MessageStatusIndicator(
    status: MessageStatus,
    modifier: Modifier = Modifier
) {
    when (status) {
        MessageStatus.PENDING -> {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Pending delivery",
                tint = AmberPending,
                modifier = modifier.size(12.dp)
            )
        }
        MessageStatus.SENT -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sent",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = modifier.size(13.dp)
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                modifier = modifier.size(14.dp)
            )
        }
        MessageStatus.READ -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Read",
                tint = ElectricCyan,
                modifier = modifier.size(14.dp)
            )
        }
        MessageStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Failed",
                tint = MaterialTheme.colorScheme.error,
                modifier = modifier.size(12.dp)
            )
        }
    }
}

/**
 * Dialog to inspect raw AES-GCM Ciphertext, IV, Signature, proving genuine E2EE
 */
@Composable
fun CiphertextInspectionDialog(
    message: MessageEntity,
    onDismiss: () -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = CyberIndigo,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cryptographic Payload",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Every message is encrypted locally with AES-256-GCM using keys derived via ECDH (secp256r1), signed with ECDSA, and synced directly peer-to-peer without server storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                CryptoFieldBlock(
                    label = "Decrypted Plaintext",
                    value = message.decryptedText,
                    highlight = true
                )

                CryptoFieldBlock(
                    label = "AES-256-GCM Ciphertext (Base64)",
                    value = message.ciphertext,
                    onCopy = { clipboard.setText(AnnotatedString(message.ciphertext)) }
                )

                CryptoFieldBlock(
                    label = "Initialization Vector (IV - 96 bits)",
                    value = message.iv,
                    onCopy = { clipboard.setText(AnnotatedString(message.iv)) }
                )

                CryptoFieldBlock(
                    label = "ECDSA Digital Signature",
                    value = message.signature.take(48) + "...",
                    onCopy = { clipboard.setText(AnnotatedString(message.signature)) }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (message.isSignatureVerified) EmeraldContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (message.isSignatureVerified) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (message.isSignatureVerified) EmeraldVerified else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (message.isSignatureVerified) "Signature verified with sender public key" else "Unverified signature",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isSignatureVerified) EmeraldVerified else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberIndigo)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun CryptoFieldBlock(
    label: String,
    value: String,
    highlight: Boolean = false,
    onCopy: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            if (onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Dialog to verify Safety Numbers for end-to-end encryption authentication
 */
@Composable
fun SafetyNumberDialog(
    peerName: String,
    safetyNumber: String,
    isVerified: Boolean,
    onVerificationChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isVerified) EmeraldVerified else CyberIndigo,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Verify Safety Numbers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Compare this 30-digit safety number with $peerName's device in person or via another trusted channel to verify end-to-end encryption and guard against MITM attacks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Visual Identicon Matrix
                KeyIdenticonMatrix(keyHash = safetyNumber.replace(" ", ""), size = 96)

                // 6 groups of 5 digits
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = safetyNumber,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(safetyNumber)) },
                            modifier = Modifier.testTag("copy_safety_number")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Safety Number", fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mark as Verified",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Authenticates that $peerName is genuine",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isVerified,
                        onCheckedChange = onVerificationChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldVerified,
                            checkedTrackColor = EmeraldContainer
                        ),
                        modifier = Modifier.testTag("verify_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberIndigo)
            ) {
                Text("Done")
            }
        }
    )
}

/**
 * Dialog to connect directly via IP Address and Port (hotspot / LAN direct connect)
 */
@Composable
fun DirectConnectDialog(
    localIp: String?,
    localPort: Int,
    onConnect: (ip: String, port: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var ipInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf("8888") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = CyberIndigo,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Direct Device Link",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Connect directly to another phone or tablet over local Wi-Fi or mobile hotspot without any server PC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Show local info so user can share with other device
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Your Device Endpoint:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${localIp ?: "Detecting..."}:$localPort",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Other devices on the same Wi-Fi or hotspot can enter this address.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = ipInput,
                    onValueChange = {
                        ipInput = it
                        errorText = null
                    },
                    label = { Text("Peer IP Address") },
                    placeholder = { Text("e.g. 192.168.1.120") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_ip_input")
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_port_input")
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedIp = ipInput.trim()
                    val port = portInput.trim().toIntOrNull() ?: 8888
                    if (trimmedIp.isEmpty()) {
                        errorText = "Please enter an IP address"
                    } else {
                        onConnect(trimmedIp, port)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberIndigo),
                modifier = Modifier.testTag("direct_connect_button")
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Full Contact Metadata & Security Verification Dialog
 * Stored locally in Room database for offline persistence
 */
@Composable
fun ContactMetadataDialog(
    contact: com.example.data.models.ContactEntity,
    onDismiss: () -> Unit,
    onUpdateNickname: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onToggleBlock: (Boolean) -> Unit,
    onToggleVerify: (Boolean) -> Unit,
    onStartChat: () -> Unit
) {
    var nicknameInput by remember(contact.customNickname) { mutableStateOf(contact.customNickname) }
    var notesInput by remember(contact.notes) { mutableStateOf(contact.notes) }
    var isEditingNickname by remember { mutableStateOf(false) }
    var isEditingNotes by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var copiedItem by remember { mutableStateOf<String?>(null) }

    val lastSeenStr = remember(contact.lastSeenTimestamp) {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date(contact.lastSeenTimestamp))
    }

    val verifiedDateStr = remember(contact.verifiedTimestamp) {
        contact.verifiedTimestamp?.let {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(it))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (contact.isVerified) EmeraldContainer else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.effectiveName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = if (contact.isVerified) EmeraldVerified else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = contact.effectiveName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (contact.customNickname.isNotBlank()) {
                            Text(
                                text = "Device: ${contact.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { onToggleFavorite(!contact.isFavorite) },
                    modifier = Modifier.testTag("contact_favorite_toggle")
                ) {
                    Icon(
                        imageVector = if (contact.isFavorite) Icons.Default.CheckCircle else Icons.Default.Check,
                        contentDescription = "Favorite",
                        tint = if (contact.isFavorite) AmberPending else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Verification Badge Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (contact.isVerified) EmeraldContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (contact.isVerified) Icons.Default.VerifiedUser else Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (contact.isVerified) EmeraldVerified else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (contact.isVerified) "Verified Contact" else "Unverified Identity",
                                    fontWeight = FontWeight.Bold,
                                    color = if (contact.isVerified) EmeraldVerified else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Switch(
                                checked = contact.isVerified,
                                onCheckedChange = onToggleVerify,
                                colors = SwitchDefaults.colors(checkedThumbColor = EmeraldVerified)
                            )
                        }
                        if (contact.isVerified && verifiedDateStr != null) {
                            Text(
                                text = "Cryptographically verified on $verifiedDateStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldVerified.copy(alpha = 0.9f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Custom Nickname (Alias) Editor
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Contact Nickname / Alias",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!isEditingNickname) {
                                TextButton(onClick = { isEditingNickname = true }) {
                                    Text("Edit")
                                }
                            }
                        }

                        if (isEditingNickname) {
                            OutlinedTextField(
                                value = nicknameInput,
                                onValueChange = { nicknameInput = it },
                                placeholder = { Text("Enter local nickname...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                TextButton(onClick = {
                                    nicknameInput = contact.customNickname
                                    isEditingNickname = false
                                }) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        onUpdateNickname(nicknameInput)
                                        isEditingNickname = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberIndigo)
                                ) {
                                    Text("Save")
                                }
                            }
                        } else {
                            Text(
                                text = contact.customNickname.ifBlank { "No nickname set (tap Edit to customize)" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (contact.customNickname.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Safety Number & Identicon Matrix
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "30-Digit Safety Number",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KeyIdenticonMatrix(
                                keyHash = contact.safetyNumber.replace(" ", ""),
                                size = 56
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.safetyNumber.ifBlank { "Generated on connection" },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(contact.safetyNumber))
                                            copiedItem = "safety_number"
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Safety Number",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    if (copiedItem == "safety_number") {
                                        Text(
                                            text = "Copied!",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldVerified
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Public Key SHA-256 Fingerprint
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Public Key Fingerprint (SHA-256)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = contact.keyFingerprint.ifBlank { "Not available" },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Peer ID: ${contact.peerId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(contact.keyFingerprint))
                                    copiedItem = "fingerprint"
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Fingerprint",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Private Contact Notes
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Local Contact Notes",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!isEditingNotes) {
                                TextButton(onClick = { isEditingNotes = true }) {
                                    Text(if (contact.notes.isBlank()) "Add" else "Edit")
                                }
                            }
                        }

                        if (isEditingNotes) {
                            OutlinedTextField(
                                value = notesInput,
                                onValueChange = { notesInput = it },
                                placeholder = { Text("Private notes (e.g. In-person meeting, phone backup)...") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                TextButton(onClick = {
                                    notesInput = contact.notes
                                    isEditingNotes = false
                                }) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        onUpdateNotes(notesInput)
                                        isEditingNotes = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberIndigo)
                                ) {
                                    Text("Save Notes")
                                }
                            }
                        } else {
                            Text(
                                text = contact.notes.ifBlank { "No notes added yet." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (contact.notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Network & Metadata Details
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Network Metadata",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Last Endpoint: ${contact.lastKnownHost.ifBlank { "Unknown" }}:${contact.lastKnownPort}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                        Text(
                            text = "Last Active: $lastSeenStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Block Peer Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Column {
                        Text(
                            text = "Block Peer",
                            fontWeight = FontWeight.SemiBold,
                            color = if (contact.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Silently ignore incoming P2P messages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = contact.isBlocked,
                        onCheckedChange = onToggleBlock,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.error,
                            checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStartChat,
                colors = ButtonDefaults.buttonColors(containerColor = CyberIndigo),
                modifier = Modifier.testTag("contact_start_chat_button")
            ) {
                Text("Start Encrypted Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
