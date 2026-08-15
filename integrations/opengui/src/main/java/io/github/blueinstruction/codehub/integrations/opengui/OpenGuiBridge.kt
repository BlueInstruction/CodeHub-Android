package io.github.blueinstruction.codehub.integrations.opengui

import javax.inject.Inject
import javax.inject.Singleton

interface OpenGuiBridge {
    val available: Boolean
}

@Singleton
class DefaultOpenGuiBridge @Inject constructor() : OpenGuiBridge {
    override val available: Boolean = false
}
