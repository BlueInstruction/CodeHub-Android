package codehub.core.permissions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BashArityTest {

    @Test
    fun `simple git command has arity 2`() {
        assertThat(BashArity.arityFor("git status")).isEqualTo(2)
    }

    @Test
    fun `git push with remote has arity 3`() {
        assertThat(BashArity.arityFor("git push origin main")).isEqualTo(3)
    }

    @Test
    fun `git checkout is arity 3`() {
        assertThat(BashArity.arityFor("git checkout feature-branch")).isEqualTo(3)
    }

    @Test
    fun `docker compose is arity 3`() {
        assertThat(BashArity.arityFor("docker compose up -d")).isEqualTo(3)
    }

    @Test
    fun `npm run is arity 3`() {
        assertThat(BashArity.arityFor("npm run build")).isEqualTo(3)
    }

    @Test
    fun `kubectl get is arity 3`() {
        assertThat(BashArity.arityFor("kubectl get pods")).isEqualTo(3)
    }

    @Test
    fun `unknown command defaults to arity 1`() {
        assertThat(BashArity.arityFor("totallymadeupcommand arg1 arg2")).isEqualTo(1)
    }

    @Test
    fun `describe returns human-friendly prefix`() {
        val description = BashArity.describe("git push --force origin main")
        assertThat(description).isEqualTo("git push")
    }

    @Test
    fun `describe handles short command`() {
        val description = BashArity.describe("ls")
        assertThat(description).isEqualTo("ls")
    }

    @Test
    fun `describe handles docker with subcommand`() {
        val description = BashArity.describe("docker stop mycontainer")
        assertThat(description).isEqualTo("docker stop")
    }

    @Test
    fun `describe handles nested docker compose`() {
        val description = BashArity.describe("docker compose logs -f web")
        assertThat(description).isEqualTo("docker compose")
    }

    @Test
    fun `empty string returns arity 0`() {
        assertThat(BashArity.arityFor("")).isEqualTo(0)
    }

    @Test
    fun `blank string returns arity 0`() {
        assertThat(BashArity.arityFor("   ")).isEqualTo(0)
    }

    @Test
    fun `pm install is arity 3`() {
        assertThat(BashArity.arityFor("pm install /data/app/example.apk")).isEqualTo(3)
    }

    @Test
    fun `fastboot flash is arity 3`() {
        assertThat(BashArity.arityFor("fastboot flash boot boot.img")).isEqualTo(3)
    }

    @Test
    fun `cmake --build is arity 3`() {
        assertThat(BashArity.arityFor("cmake --build build --target all")).isEqualTo(3)
    }
}
