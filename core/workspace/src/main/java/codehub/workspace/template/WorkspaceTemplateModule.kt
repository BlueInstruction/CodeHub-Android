package codehub.workspace.template

import android.content.Context
import codehub.core.diagnostics.DiagnosticSink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkspaceTemplateModule {

    @Provides
    @Singleton
    fun provideWrapperAssets(
        @ApplicationContext ctx: Context
    ): WrapperAssets = AndroidWrapperAssets(ctx)

    @Provides
    @Singleton
    fun provideProjectGenerator(
        sink: DiagnosticSink,
        wrapperAssets: WrapperAssets
    ): ProjectGenerator = ProjectGenerator(sink, wrapperAssets)
}
