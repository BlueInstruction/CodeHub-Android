package codehub.integrations.acs

import javax.inject.Inject
import javax.inject.Singleton

interface AcsComponents {
    val enabled: Boolean
}

@Singleton
class DefaultAcsComponents @Inject constructor() : AcsComponents {
    override val enabled: Boolean = false
}
