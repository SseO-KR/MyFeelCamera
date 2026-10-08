package com.newfeelcamera.data.billing

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal object PurchaseSignatureVerifier {
    fun verify(
        base64PublicKey: String,
        signedData: String,
        base64Signature: String,
    ): Boolean {
        if (base64PublicKey.isBlank() || signedData.isBlank() || base64Signature.isBlank()) {
            return false
        }

        return try {
            val keyBytes = Base64.getDecoder().decode(base64PublicKey)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))

            val verifier = Signature.getInstance("SHA1withRSA")
            verifier.initVerify(publicKey)
            verifier.update(signedData.toByteArray(Charsets.UTF_8))
            verifier.verify(Base64.getDecoder().decode(base64Signature))
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
