package codehub.terminal.termux

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import codehub.terminal.api.TerminalBackend
import codehub.terminal.api.TerminalBackendProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TermuxModule {

    @Provides
    @Singleton
    fun provideTerminalProviderMap(
        termux: TermuxBackendProvider
    ): Map<TerminalBackend, @JvmSuppressWildcards TerminalBackendProvider> =
        mapOf(TerminalBackend.Termux to termux)
}
