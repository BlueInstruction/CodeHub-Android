package codehub.terminal.termux.pty

import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

interface PtyTerminalSessionClientFactory {
    fun create(
        sessionId: String,
        outputSink: kotlinx.coroutines.flow.MutableSharedFlow<codehub.terminal.api.TerminalOutput>,
        onExit: (Int) -> Unit,
        onTitleChanged: (String) -> Unit
    ): TerminalSessionClient
}
