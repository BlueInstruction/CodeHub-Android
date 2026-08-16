package codehub.ui.nav

import kotlinx.serialization.Serializable

sealed interface CodeHubRoute {
    @Serializable data object Workspace : CodeHubRoute
    @Serializable data object Editor : CodeHubRoute
    @Serializable data object Terminal : CodeHubRoute
    @Serializable data object Git : CodeHubRoute
    @Serializable data object Build : CodeHubRoute
    @Serializable data object Ai : CodeHubRoute
    @Serializable data object Devices : CodeHubRoute
}
