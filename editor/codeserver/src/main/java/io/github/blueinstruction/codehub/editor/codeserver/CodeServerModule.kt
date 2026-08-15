package io.github.blueinstruction.codehub.editor.codeserver

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.blueinstruction.codehub.editor.api.EditorService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CodeServerModule {

    @Binds
    @Singleton
    abstract fun bindEditorService(impl: CodeServerEditorService): EditorService

    @Binds
    @Singleton
    abstract fun bindBackendProvider(impl: TermuxCodeServerBackendProvider): CodeServerBackendProvider
}
