package codehub.terminal.termux.pty

import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow

@Singleton
class DefaultPtyTerminalSessionClientFactory @Inject constructor() : PtyTerminalSessionClientFactory {

    override fun create(
        sessionId: String,
        outputSink: MutableSharedFlow<codehub.terminal.api.TerminalOutput>,
        onExit: (Int) -> Unit,
        onTitleChanged: (String) -> Unit
    ): TerminalSessionClient = ClientImpl(
        sessionId = sessionId,
        outputSink = outputSink,
        onExit = onExit,
        onTitleChanged = onTitleChanged
    )

    private class ClientImpl(
        private val sessionId: String,
        private val outputSink: MutableSharedFlow<codehub.terminal.api.TerminalOutput>,
        private val onExit: (Int) -> Unit,
        private val onTitleChanged: (String) -> Unit
    ) : TerminalSessionClient {

        override fun onTextChanged(changedSession: TerminalSession) {
            val emulator = changedSession.emulator ?: return
            val text = emulator.screen.transcriptText
            if (text.isNotEmpty()) {
                outputSink.tryEmit(codehub.terminal.api.TerminalOutput.Stdout(text, sessionId))
            }
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            onTitleChanged(changedSession.title)
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            onExit(finishedSession.exitStatus)
            outputSink.tryEmit(
                codehub.terminal.api.TerminalOutput.Exit(sessionId, finishedSession.exitStatus)
            )
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}

        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

        override fun getTerminalCursorStyle(): Integer? = null

        override fun logError(tag: String, message: String) { Log.e(tag, message) }
        override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
        override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
        override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
        override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }

        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
            Log.e(tag, message, e)
        }

        override fun logStackTrace(tag: String, e: Exception) {
            Log.e(tag, e.message, e)
        }
    }
}
