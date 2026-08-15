package io.github.blueinstruction.codehub.integrations.openclaw

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OpenClawModule {

    @Binds
    @Singleton
    abstract fun bindBridge(impl: DefaultOpenClawBridge): OpenClawBridge
}
