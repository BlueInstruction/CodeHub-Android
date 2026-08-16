package codehub.terminal.termux

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import codehub.terminal.api.TerminalBackend
import codehub.terminal.api.TerminalBackendProvider
import codehub.terminal.termux.pty.PtyBackendProvider
import codehub.terminal.termux.pty.PtyTerminalSessionClientFactory
import codehub.terminal.termux.pty.DefaultPtyTerminalSessionClientFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TermuxModule {

    @Provides
    @Singleton
    fun provideTerminalProviderMap(
        pty: PtyBackendProvider
    ): Map<TerminalBackend, @JvmSuppressWildcards TerminalBackendProvider> =
        mapOf(TerminalBackend.Termux to pty)

    @Provides
    @Singleton
    fun providePtyClientFactory(
        impl: DefaultPtyTerminalSessionClientFactory
    ): PtyTerminalSessionClientFactory = impl
}
