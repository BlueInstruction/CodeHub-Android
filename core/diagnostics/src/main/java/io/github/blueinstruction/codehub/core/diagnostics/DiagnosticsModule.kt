package io.github.blueinstruction.codehub.core.diagnostics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindDiagnosticSink(impl: InMemoryDiagnosticSink): DiagnosticSink
}
