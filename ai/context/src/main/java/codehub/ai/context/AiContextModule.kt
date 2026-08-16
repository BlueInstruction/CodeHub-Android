package codehub.ai.context

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiContextModule {

    @Binds
    @Singleton
    abstract fun bindContextRetriever(impl: DefaultContextRetriever): ContextRetriever
}
