package com.example.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedMessagePayload(
    val ciphertext: String,
    val iv: String,
    val signature: String,
    val senderPublicKey: String,
    val senderId: String
)

data class DecryptionResult(
    val plaintext: String,
    val isSignatureValid: Boolean
)

class CryptoManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cipher_mesh_identity", Context.MODE_PRIVATE)

    private val keyPair: KeyPair by lazy {
        loadOrGenerateKeyPair()
    }

    val publicKey: PublicKey
        get() = keyPair.public

    val privateKey: PrivateKey
        get() = keyPair.private

    val publicKeyBase64: String by lazy {
        Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    val peerId: String by lazy {
        computeFingerprint(publicKeyBase64).take(12)
    }

    fun getDisplayName(): String {
        return prefs.getString("display_name", null) ?: "Device-${peerId.take(4).uppercase()}"
    }

    fun setDisplayName(name: String) {
        prefs.edit().putString("display_name", name).apply()
    }

    private fun loadOrGenerateKeyPair(): KeyPair {
        val privBase64 = prefs.getString("priv_key", null)
        val pubBase64 = prefs.getString("pub_key", null)

        if (privBase64 != null && pubBase64 != null) {
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val privBytes = Base64.decode(privBase64, Base64.DEFAULT)
                val pubBytes = Base64.decode(pubBase64, Base64.DEFAULT)

                val privSpec = PKCS8EncodedKeySpec(privBytes)
                val pubSpec = X509EncodedKeySpec(pubBytes)

                val privKey = keyFactory.generatePrivate(privSpec)
                val pubKey = keyFactory.generatePublic(pubSpec)
                return KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                // If corrupted, fallback to generation
            }
        }

        // Generate standard NIST P-256 (secp256r1) EC KeyPair
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val newPair = kpg.generateKeyPair()

        val privString = Base64.encodeToString(newPair.private.encoded, Base64.NO_WRAP)
        val pubString = Base64.encodeToString(newPair.public.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString("priv_key", privString)
            .putString("pub_key", pubString)
            .apply()

        return newPair
    }

    /**
     * Parse an X.509 Base64 encoded public key
     */
    fun parsePublicKey(pubKeyBase64: String): PublicKey {
        val keyBytes = Base64.decode(pubKeyBase64, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(spec)
    }

    /**
     * Perform ECDH Key Agreement and derive a 256-bit AES symmetric key
     */
    private fun deriveSharedKey(peerPublicKey: PublicKey): SecretKeySpec {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Derive 256-bit key using SHA-256 over shared secret
        val sha256 = MessageDigest.getInstance("SHA-256")
        val derivedKeyBytes = sha256.digest(sharedSecret)
        return SecretKeySpec(derivedKeyBytes, "AES")
    }

    /**
     * Encrypt message with AES-256-GCM using derived ECDH secret
     * and digitally sign the ciphertext with ECDSA
     */
    fun encrypt(plaintext: String, recipientPublicKeyBase64: String): EncryptedMessagePayload {
        val recipientPubKey = parsePublicKey(recipientPublicKeyBase64)
        val aesKey = deriveSharedKey(recipientPubKey)

        // 12-byte IV for GCM
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(128, iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
        val ciphertextBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Sign the ciphertext using sender's private key
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(ciphertextBytes)
        val signatureBytes = signer.sign()

        return EncryptedMessagePayload(
            ciphertext = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            signature = Base64.encodeToString(signatureBytes, Base64.NO_WRAP),
            senderPublicKey = publicKeyBase64,
            senderId = peerId
        )
    }

    /**
     * Decrypt AES-256-GCM ciphertext using derived ECDH secret
     * and verify ECDSA sender signature
     */
    fun decrypt(
        ciphertextBase64: String,
        ivBase64: String,
        signatureBase64: String,
        senderPublicKeyBase64: String
    ): DecryptionResult {
        val senderPubKey = parsePublicKey(senderPublicKeyBase64)
        val aesKey = deriveSharedKey(senderPubKey)

        val ciphertextBytes = Base64.decode(ciphertextBase64, Base64.DEFAULT)
        val ivBytes = Base64.decode(ivBase64, Base64.DEFAULT)
        val signatureBytes = Base64.decode(signatureBase64, Base64.DEFAULT)

        // Verify digital signature
        var isSignatureValid = false
        try {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(senderPubKey)
            verifier.update(ciphertextBytes)
            isSignatureValid = verifier.verify(signatureBytes)
        } catch (e: Exception) {
            isSignatureValid = false
        }

        // Decrypt ciphertext
        val gcmSpec = GCMParameterSpec(128, ivBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
        val plaintextBytes = cipher.doFinal(ciphertextBytes)
        val plaintext = String(plaintextBytes, Charsets.UTF_8)

        return DecryptionResult(
            plaintext = plaintext,
            isSignatureValid = isSignatureValid
        )
    }

    /**
     * Generate Safety Number / Fingerprint for MITM verification.
     * Computes SHA-256 over lexicographically sorted public keys
     * and formats into 6 groups of 5 digits.
     */
    fun computeSafetyNumber(peerPublicKeyBase64: String): String {
        val sortedKeys = if (publicKeyBase64 < peerPublicKeyBase64) {
            publicKeyBase64 + peerPublicKeyBase64
        } else {
            peerPublicKeyBase64 + publicKeyBase64
        }

        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(sortedKeys.toByteArray(Charsets.UTF_8))

        // Convert hash bytes to a 30-digit decimal safety string
        val sb = StringBuilder()
        for (i in 0 until 15) {
            val byteVal = (hash[i].toInt() and 0xFF) * 256 + (hash[i + 1].toInt() and 0xFF)
            val chunk = String.format("%05d", byteVal % 100000)
            sb.append(chunk)
            if (sb.length >= 30) break
        }

        val fullDigits = sb.toString().padEnd(30, '0').take(30)
        return fullDigits.chunked(5).take(6).joinToString(" ")
    }

    /**
     * Compute short readable SHA-256 hex fingerprint
     */
    fun computeFingerprint(keyBase64: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(keyBase64.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02X".format(it) }
    }
}
