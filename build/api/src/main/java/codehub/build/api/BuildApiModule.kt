package codehub.build.api

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BuildApiModule {

    @Binds
    @Singleton
    abstract fun bindBuildService(impl: DefaultBuildService): BuildService
}
