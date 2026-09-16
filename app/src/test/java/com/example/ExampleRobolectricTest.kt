package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Secure Messenger", appName)
  }

  @Test
  fun `test end to end encryption and signature verification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Simulate Device A
    val deviceA = CryptoManager(context)
    val pubA = deviceA.publicKeyBase64

    // Test encryption for self or peer
    val plaintext = "Hello peer, zero-server end-to-end encryption test!"
    val encrypted = deviceA.encrypt(plaintext, pubA)

    assertNotEquals(plaintext, encrypted.ciphertext)
    assertTrue(encrypted.ciphertext.isNotEmpty())
    assertTrue(encrypted.iv.isNotEmpty())
    assertTrue(encrypted.signature.isNotEmpty())

    // Decrypt
    val result = deviceA.decrypt(
      ciphertextBase64 = encrypted.ciphertext,
      ivBase64 = encrypted.iv,
      signatureBase64 = encrypted.signature,
      senderPublicKeyBase64 = pubA
    )

    assertEquals(plaintext, result.plaintext)
    assertTrue(result.isSignatureValid)
  }
}

