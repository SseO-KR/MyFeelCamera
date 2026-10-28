package com.sseo.feelcamera.data.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PurchaseSignatureVerifierTest {
    @Test
    fun acceptsValidSignatureAndRejectsChangedData() {
        val keyPair = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
        val signedData = """{"productId":"feelcamera_unlimited_capture"}"""

        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(keyPair.private)
        signer.update(signedData.toByteArray(Charsets.UTF_8))

        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val signature = Base64.getEncoder().encodeToString(signer.sign())

        assertTrue(PurchaseSignatureVerifier.verify(publicKey, signedData, signature))
        assertFalse(PurchaseSignatureVerifier.verify(publicKey, signedData + " ", signature))
        assertFalse(PurchaseSignatureVerifier.verify("invalid-key", signedData, signature))
    }
}
