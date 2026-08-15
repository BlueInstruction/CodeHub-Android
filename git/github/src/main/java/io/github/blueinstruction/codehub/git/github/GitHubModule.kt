package io.github.blueinstruction.codehub.git.github

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GitHubModule {

    @Binds
    @Singleton
    abstract fun bindGitHubClient(impl: HttpGitHubClient): GitHubClient
}
