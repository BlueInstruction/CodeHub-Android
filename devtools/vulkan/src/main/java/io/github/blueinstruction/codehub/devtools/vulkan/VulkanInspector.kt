package io.github.blueinstruction.codehub.devtools.vulkan

import kotlinx.serialization.Serializable

@Serializable
data class VulkanDeviceInfo(
    val deviceName: String,
    val apiVersion: String,
    val driverVersion: String,
    val vendorId: String,
    val deviceId: String,
    val deviceType: String,
    val maxMemoryAllocationCount: Int,
    val maxBoundDescriptorSets: Int,
    val timestampPeriod: Float
)

@Serializable
data class VulkanInstanceInfo(
    val apiVersion: String,
    val layers: List<String>,
    val extensions: List<String>,
    val physicalDevices: List<VulkanDeviceInfo>
)

interface VulkanInspector {
    suspend fun inspect(): VulkanInstanceInfo
    suspend fun available(): Boolean
}
