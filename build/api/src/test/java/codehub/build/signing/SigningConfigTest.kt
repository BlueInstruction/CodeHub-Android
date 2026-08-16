package codehub.build.signing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SigningConfigTest {

    @Test
    fun `debug config has android store and key passwords`() {
        val config = SigningConfig.debug("/tmp/debug.keystore")
        assertThat(config.storePassword).isEqualTo("android")
        assertThat(config.keyPassword).isEqualTo("android")
        assertThat(config.keyAlias).isEqualTo("androiddebugkey")
    }

    @Test
    fun `debug config uses RSA 2048`() {
        val config = SigningConfig.debug("/tmp/debug.keystore")
        assertThat(config.keyAlgorithm).isEqualTo("RSA")
        assertThat(config.keySize).isEqualTo(2048)
    }

    @Test
    fun `debug config has 10000 day validity`() {
        val config = SigningConfig.debug("/tmp/debug.keystore")
        assertThat(config.validityDays).isEqualTo(10000)
    }

    @Test
    fun `custom config preserves all fields`() {
        val config = SigningConfig(
            storeFile = "/tmp/release.keystore",
            storePassword = "secret",
            keyAlias = "release-key",
            keyPassword = "keysecret",
            keyAlgorithm = "EC",
            keySize = 256,
            validityDays = 365
        )
        assertThat(config.storeFile).isEqualTo("/tmp/release.keystore")
        assertThat(config.storePassword).isEqualTo("secret")
        assertThat(config.keyAlias).isEqualTo("release-key")
        assertThat(config.keyPassword).isEqualTo("keysecret")
        assertThat(config.keyAlgorithm).isEqualTo("EC")
        assertThat(config.keySize).isEqualTo(256)
        assertThat(config.validityDays).isEqualTo(365)
    }
}
