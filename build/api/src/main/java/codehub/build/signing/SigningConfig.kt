package codehub.build.signing

import kotlinx.serialization.Serializable

@Serializable
data class SigningConfig(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val keyAlgorithm: String = "RSA",
    val keySize: Int = 2048,
    val validityDays: Int = 10000
) {
    companion object {
        fun debug(storeFile: String): SigningConfig = SigningConfig(
            storeFile = storeFile,
            storePassword = "android",
            keyAlias = "androiddebugkey",
            keyPassword = "android",
            keyAlgorithm = "RSA",
            keySize = 2048,
            validityDays = 10000
        )
    }
}

@Serializable
data class KeystoreResult(
    val config: SigningConfig,
    val created: Boolean,
    val existed: Boolean,
    val success: Boolean,
    val message: String
)
