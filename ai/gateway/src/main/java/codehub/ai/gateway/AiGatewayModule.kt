package codehub.ai.gateway

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiGatewayModule {

    @Binds
    @Singleton
    abstract fun bindAiGateway(impl: DefaultAiGateway): AiGateway
}
