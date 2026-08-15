package io.github.blueinstruction.codehub.terminal.api

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TerminalApiModule {

    @Binds
    @Singleton
    abstract fun bindTerminalService(impl: DefaultTerminalService): TerminalService
}
