package codehub.terminal.termux.pty

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PtyEnvironmentTest {

    @Test
    fun `explicit shell takes precedence`() {
        assertThat(PtyEnvironment.resolveShell("/bin/zsh")).isEqualTo("/bin/zsh")
    }

    @Test
    fun `blank explicit returns fallback`() {
        assertThat(PtyEnvironment.resolveShell("")).isNotEqualTo("")
        assertThat(PtyEnvironment.resolveShell("   ")).isNotEqualTo("   ")
    }

    @Test
    fun `null returns fallback`() {
        val resolved = PtyEnvironment.resolveShell(null)
        assertThat(resolved).isNotEmpty()
    }

    @Test
    fun `build returns non-empty env array`() {
        val env = PtyEnvironment.build("/tmp/work")
        assertThat(env).isNotEmpty()
        assertThat(env.size).isAtLeast(8)
    }

    @Test
    fun `build includes PWD pointing to cwd`() {
        val env = PtyEnvironment.build("/storage/emulated/0/project")
        val pwd = env.firstOrNull { it.startsWith("PWD=") }
        assertThat(pwd).isEqualTo("PWD=/storage/emulated/0/project")
    }

    @Test
    fun `build includes HOME`() {
        val env = PtyEnvironment.build("/tmp")
        val home = env.firstOrNull { it.startsWith("HOME=") }
        assertThat(home).isNotNull()
        assertThat(home).isNotEqualTo("HOME=")
    }

    @Test
    fun `build includes PATH`() {
        val env = PtyEnvironment.build("/tmp")
        val path = env.firstOrNull { it.startsWith("PATH=") }
        assertThat(path).isNotNull()
        assertThat(path).contains("/system/bin")
    }

    @Test
    fun `build includes TERM for xterm-256color`() {
        val env = PtyEnvironment.build("/tmp")
        val term = env.firstOrNull { it.startsWith("TERM=") }
        assertThat(term).isEqualTo("TERM=xterm-256color")
    }

    @Test
    fun `build includes LANG for UTF-8`() {
        val env = PtyEnvironment.build("/tmp")
        val lang = env.firstOrNull { it.startsWith("LANG=") }
        assertThat(lang).isEqualTo("LANG=en_US.UTF-8")
    }

    @Test
    fun `build includes LD_LIBRARY_PATH`() {
        val env = PtyEnvironment.build("/tmp")
        val ldPath = env.firstOrNull { it.startsWith("LD_LIBRARY_PATH=") }
        assertThat(ldPath).isNotNull()
    }

    @Test
    fun `build includes Android framework env`() {
        val env = PtyEnvironment.build("/tmp")
        assertThat(env.any { it == "ANDROID_DATA=/data" }).isTrue()
        assertThat(env.any { it == "ANDROID_ROOT=/system" }).isTrue()
    }
}
