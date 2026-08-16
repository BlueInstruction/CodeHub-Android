package codehub.workspace.template

import codehub.core.diagnostics.DiagnosticSink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkspaceTemplateModule {

    @Provides
    @Singleton
    fun provideProjectGenerator(
        sink: DiagnosticSink
    ): ProjectGenerator = ProjectGenerator(sink)
}
