package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultSecurityManager(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "docuverse_vault_aes_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_SIZE = 12
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptFileToVault(sourceFile: File, vaultDestFile: File): File {
        vaultDestFile.parentFile?.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val plainBytes = sourceFile.readBytes()
        val encryptedBytes = cipher.doFinal(plainBytes)

        vaultDestFile.outputStream().use { out ->
            out.write(iv.size)
            out.write(iv)
            out.write(encryptedBytes)
        }
        return vaultDestFile
    }

    fun decryptVaultFileTo(vaultFile: File, destPlainFile: File): File {
        destPlainFile.parentFile?.mkdirs()
        val allBytes = vaultFile.readBytes()
        require(allBytes.size > IV_SIZE + 1) { "Invalid encrypted vault file." }
        val ivLen = allBytes[0].toInt() and 0xFF
        val iv = allBytes.copyOfRange(1, 1 + ivLen)
        val cipherBytes = allBytes.copyOfRange(1 + ivLen, allBytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        destPlainFile.writeBytes(plainBytes)
        return destPlainFile
    }

    fun hashPin(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val digest = sha256(salt + pin.toByteArray(Charsets.UTF_8))
        return "${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(digest, Base64.NO_WRAP)}"
    }

    fun verifyPin(pin: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val expected = Base64.decode(parts[1], Base64.NO_WRAP)
        val actual = sha256(salt + pin.toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(expected, actual)
    }

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .build()
        prompt.authenticate(info)
    }
}
