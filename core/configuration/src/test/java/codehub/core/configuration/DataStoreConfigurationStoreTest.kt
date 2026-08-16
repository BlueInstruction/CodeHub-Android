package codehub.core.configuration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.Path

class DataStoreConfigurationStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun newStore(): DataStoreConfigurationStore {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tmp.newFile("prefs.pb") }
        )
        return DataStoreConfigurationStore(store)
    }

    @Test
    fun `current returns default when no prior config`() = runTest {
        val store = newStore()
        val current = store.current()
        assertThat(current.workspaceRootPath).isNotEmpty()
        assertThat(current.ai.defaultProvider).isEqualTo("offline")
    }

    @Test
    fun `update persists new value`() = runTest {
        val store = newStore()
        store.update { it.copy(terminal = it.terminal.copy(defaultShell = "/bin/sh")) }
        val current = store.current()
        assertThat(current.terminal.defaultShell).isEqualTo("/bin/sh")
    }

    @Test
    fun `reset removes overrides`() = runTest {
        val store = newStore()
        store.update { it.copy(editor = it.editor.copy(port = 9999)) }
        store.reset()
        val current = store.current()
        assertThat(current.editor.port).isEqualTo(8443)
    }
}
