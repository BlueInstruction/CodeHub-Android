package codehub.ai.permissions

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiPermissionsModule {

    @Binds
    @Singleton
    abstract fun bindAuditLog(impl: InMemoryAgentAuditLog): AgentAuditLog
}
