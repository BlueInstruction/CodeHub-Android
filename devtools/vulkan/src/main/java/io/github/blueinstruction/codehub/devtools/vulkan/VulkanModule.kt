package io.github.blueinstruction.codehub.devtools.vulkan

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VulkanModule {

    @Binds
    @Singleton
    abstract fun bindVulkanInspector(impl: StubVulkanInspector): VulkanInspector
}
