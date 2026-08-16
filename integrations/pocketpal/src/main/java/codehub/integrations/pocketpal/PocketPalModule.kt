package codehub.integrations.pocketpal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PocketPalModule {

    @Binds
    @Singleton
    abstract fun bindBridge(impl: DefaultPocketPalBridge): PocketPalBridge
}
