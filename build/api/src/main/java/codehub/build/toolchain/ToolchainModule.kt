package codehub.build.toolchain

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ToolchainModule {

    @Provides
    @Singleton
    fun provideToolchainManager(
        @ApplicationContext ctx: android.content.Context,
        runner: codehub.core.process.ProcessRunner,
        sink: codehub.core.diagnostics.DiagnosticSink
    ): ToolchainManager = ToolchainManager(runner, sink, ctx)

    @Provides
    @Singleton
    fun provideToolchainInstaller(
        @ApplicationContext ctx: android.content.Context,
        runner: codehub.core.process.ProcessRunner,
        sink: codehub.core.diagnostics.DiagnosticSink,
        manager: ToolchainManager
    ): ToolchainInstaller = ToolchainInstaller(runner, sink, ctx, manager)

    @Provides
    @Singleton
    fun provideDebugKeystoreGenerator(
        runner: codehub.core.process.ProcessRunner,
        sink: codehub.core.diagnostics.DiagnosticSink,
        manager: ToolchainManager
    ): codehub.build.signing.DebugKeystoreGenerator =
        codehub.build.signing.DebugKeystoreGenerator(runner, sink, manager)
}
