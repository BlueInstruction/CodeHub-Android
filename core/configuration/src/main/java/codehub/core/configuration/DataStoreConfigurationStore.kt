package codehub.core.configuration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreConfigurationStore @Inject constructor(
    private val store: DataStore<Preferences>
) : ConfigurationStore {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    private val state = MutableStateFlow(loadDefault())
    private val stateFlow: StateFlow<CodeHubConfig> = state.asStateFlow()

    override fun observe(): Flow<CodeHubConfig> =
        store.data.map { prefs ->
            prefs[CONFIG_KEY]?.let { json.decodeFromString<CodeHubConfig>(it) } ?: loadDefault()
        }

    override suspend fun current(): CodeHubConfig = stateFlow.value

    override suspend fun update(transform: (CodeHubConfig) -> CodeHubConfig) {
        store.edit { prefs ->
            val current = prefs[CONFIG_KEY]?.let { json.decodeFromString<CodeHubConfig>(it) } ?: loadDefault()
            val next = transform(current)
            prefs[CONFIG_KEY] = json.encodeToString(next)
            state.value = next
        }
    }

    override suspend fun reset() {
        store.edit { it.remove(CONFIG_KEY) }
        state.value = loadDefault()
    }

    private fun loadDefault(): CodeHubConfig = CodeHubConfig(
        workspaceRootPath = "/storage/emulated/0/CodeHub/workspaces",
        cacheDirPath = "/storage/emulated/0/CodeHub/cache"
    )

    companion object {
        private val CONFIG_KEY = stringPreferencesKey("codehub.config.json")
    }
}
