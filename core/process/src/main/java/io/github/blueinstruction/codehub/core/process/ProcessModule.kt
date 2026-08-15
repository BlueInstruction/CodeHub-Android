package io.github.blueinstruction.codehub.core.process

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessModule {
    @Binds
    @Singleton
    abstract fun bindProcessRunner(impl: JavaProcessRunner): ProcessRunner
}
