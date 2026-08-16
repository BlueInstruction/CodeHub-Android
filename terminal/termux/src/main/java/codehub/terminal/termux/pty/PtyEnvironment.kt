package codehub.terminal.termux.pty

import java.io.File

object PtyEnvironment {

    val TERMUX_HOME = "/data/data/com.termux/files/home"
    val TERMUX_PREFIX = "/data/data/com.termux/files/usr"

    fun resolveShell(explicit: String?): String {
        if (!explicit.isNullOrBlank()) return explicit
        if (File(TERMUX_PREFIX + "/bin/bash").exists()) return TERMUX_PREFIX + "/bin/bash"
        return "/system/bin/sh"
    }

    fun build(cwd: String): Array<String> {
        val home = if (File(TERMUX_HOME).isDirectory) TERMUX_HOME else (System.getProperty("user.home") ?: "/")
        val prefix = if (File(TERMUX_PREFIX).isDirectory) TERMUX_PREFIX else "/system"
        val path = prefix + "/bin:" + prefix + "/bin/applets:/system/bin:/system/xbin"
        return arrayOf(
            "HOME=$home",
            "PREFIX=$prefix",
            "PATH=$path",
            "LD_LIBRARY_PATH=$prefix/lib",
            "LANG=en_US.UTF-8",
            "TERM=xterm-256color",
            "TMPDIR=$prefix/tmp",
            "ANDROID_DATA=/data",
            "ANDROID_ROOT=/system",
            "PWD=$cwd"
        )
    }
}
