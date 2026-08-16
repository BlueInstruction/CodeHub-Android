package codehub.devtools.vulkan

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StubVulkanInspector @Inject constructor() : VulkanInspector {

    override suspend fun available(): Boolean = false

    override suspend fun inspect(): VulkanInstanceInfo = VulkanInstanceInfo(
        apiVersion = "0.0.0",
        layers = emptyList(),
        extensions = emptyList(),
        physicalDevices = emptyList()
    )
}
