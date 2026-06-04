package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.model.*
import dev.conduit.desktop.session.DaemonManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Display-friendly loader types including Vanilla (no loader). */
enum class LoaderDisplayType(val displayName: String) {
    NEOFORGE("NeoForge"),
    FABRIC("Fabric"),
    QUILT("Quilt"),
    FORGE("Forge"),
    VANILLA("Vanilla"),
}

data class CreateInstanceState(
    // Form fields
    val name: String = "",
    val mcVersion: String = "",
    val port: Int = 25565,
    val maxPlayers: Int = 20,

    // Loader selection
    val loaderType: LoaderDisplayType = LoaderDisplayType.NEOFORGE,
    val selectedLoaderVersion: String = "",
    val availableLoaders: List<AvailableLoader> = emptyList(),
    val loaderVersionsLoading: Boolean = false,

    // Version data
    val mcVersions: List<MinecraftVersion> = emptyList(),
    val versionsLoading: Boolean = false,

    // Daemon info
    val daemonName: String = "",

    // Submission state
    val isCreating: Boolean = false,
    val error: String? = null,
)

class CreateInstanceViewModel(
    private val daemonId: String,
    private val daemonManager: DaemonManager,
) : ViewModel() {

    private val apiClient get() = daemonManager.getSession(daemonId)?.getApi() ?: error("No session")
    private val _state = MutableStateFlow(CreateInstanceState())
    val state: StateFlow<CreateInstanceState> = _state

    private var fetchLoadersJob: Job? = null

    init {
        val session = daemonManager.getSession(daemonId)
        _state.value = _state.value.copy(daemonName = session?.daemonName ?: "")
        loadMinecraftVersions()
    }

    // --- State updaters ---

    fun updateName(name: String) { _state.update { it.copy(name = name) } }

    fun updateMcVersion(version: String) {
        _state.update { it.copy(mcVersion = version) }
        fetchAvailableVersions()
    }

    fun updatePort(port: String) {
        port.toIntOrNull()?.let { _state.update { s -> s.copy(port = it) } }
    }

    fun updateMaxPlayers(maxPlayers: String) {
        maxPlayers.toIntOrNull()?.let { _state.update { s -> s.copy(maxPlayers = it) } }
    }

    fun updateLoaderType(type: LoaderDisplayType) {
        _state.update { it.copy(loaderType = type, selectedLoaderVersion = "") }
        if (type != LoaderDisplayType.VANILLA) {
            fetchAvailableVersions()
        }
    }

    fun updateLoaderVersion(version: String) {
        _state.update { it.copy(selectedLoaderVersion = version) }
    }

    // --- Data loading ---

    private fun loadMinecraftVersions() {
        viewModelScope.launch {
            _state.update { it.copy(versionsLoading = true) }
            try {
                val response = apiClient.listMinecraftVersions()
                val latest = response.versions.firstOrNull { it.type == "release" }
                _state.update {
                    it.copy(
                        mcVersions = response.versions,
                        mcVersion = it.mcVersion.ifEmpty { latest?.id ?: "1.21.4" },
                        versionsLoading = false,
                    )
                }
                // Auto-fetch available loader versions for the default MC version
                fetchAvailableVersions()
            } catch (e: Exception) {
                _state.update { it.copy(versionsLoading = false, error = "Failed to load versions: ${e.message}") }
            }
        }
    }

    private fun fetchAvailableVersions() {
        fetchLoadersJob?.cancel()
        val mcVersion = _state.value.mcVersion
        if (mcVersion.isBlank()) return

        fetchLoadersJob = viewModelScope.launch {
            _state.update { it.copy(loaderVersionsLoading = true, availableLoaders = emptyList(), selectedLoaderVersion = "") }
            try {
                val loaders = apiClient.listAvailableLoadersByMcVersion(mcVersion)
                _state.update { state ->
                    val selectedType = state.loaderType
                    if (selectedType == LoaderDisplayType.VANILLA) return@update state
                    val backendType = toBackendType(selectedType) ?: return@update state
                    val matchingLoader = loaders.find { it.type == backendType }
                    val latestVersion = matchingLoader?.versions?.firstOrNull()
                    state.copy(
                        availableLoaders = loaders,
                        selectedLoaderVersion = latestVersion ?: "",
                        loaderVersionsLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loaderVersionsLoading = false) }
            }
        }
    }

    // --- Submission ---

    fun create(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) { _state.update { it.copy(error = "Name is required") }; return }
        if (s.mcVersion.isBlank()) { _state.update { it.copy(error = "Version is required") }; return }

        _state.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            try {
                // Step 1: Create instance
                val instance = apiClient.createInstance(
                    CreateInstanceRequest(
                        name = s.name,
                        mcVersion = s.mcVersion,
                        mcPort = s.port,
                        maxPlayers = s.maxPlayers,
                    )
                )

                // Step 2: Install loader if not Vanilla
                if (s.loaderType != LoaderDisplayType.VANILLA) {
                    val backendType = toBackendType(s.loaderType)
                    val version = s.selectedLoaderVersion
                    if (backendType != null && version.isNotBlank()) {
                        apiClient.installLoader(
                            instance.id,
                            InstallLoaderRequest(type = backendType, version = version),
                        )
                    }
                }

                _state.update { it.copy(isCreating = false) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isCreating = false, error = "Create failed: ${e.message}") }
            }
        }
    }

    companion object {
        /** Maps display loader type to the backend LoaderType enum. */
        fun toBackendType(type: LoaderDisplayType): LoaderType? = when (type) {
            LoaderDisplayType.NEOFORGE -> LoaderType.NEOFORGE
            LoaderDisplayType.FABRIC -> LoaderType.FABRIC
            LoaderDisplayType.QUILT -> LoaderType.QUILT
            LoaderDisplayType.FORGE -> LoaderType.FORGE
            LoaderDisplayType.VANILLA -> null
        }

        /** Maps backend LoaderType to display type. */
        fun toDisplayType(type: LoaderType): LoaderDisplayType = when (type) {
            LoaderType.NEOFORGE -> LoaderDisplayType.NEOFORGE
            LoaderType.FABRIC -> LoaderDisplayType.FABRIC
            LoaderType.QUILT -> LoaderDisplayType.QUILT
            LoaderType.FORGE -> LoaderDisplayType.FORGE
        }
    }
}
