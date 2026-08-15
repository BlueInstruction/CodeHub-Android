package io.github.blueinstruction.codehub.ai.agents

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentRunnerModule {

    @Binds
    @Singleton
    abstract fun bindAgentRunner(impl: DefaultAgentRunner): AgentRunner
}
