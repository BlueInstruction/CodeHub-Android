package io.github.blueinstruction.codehub.integrations.hermes

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HermesModule {

    @Binds
    @Singleton
    abstract fun bindBridge(impl: DefaultHermesBridge): HermesBridge
}
