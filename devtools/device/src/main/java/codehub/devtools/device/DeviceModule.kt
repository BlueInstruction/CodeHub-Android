package codehub.devtools.device

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import codehub.devtools.memory.MemoryMonitor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeviceModule {

    @Provides
    @Singleton
    fun provideMemoryMonitor(@ApplicationContext ctx: android.content.Context): MemoryMonitor =
        MemoryMonitor(ctx)

    @Provides
    @Singleton
    fun provideDeviceMonitor(
        @ApplicationContext ctx: android.content.Context,
        memoryMonitor: MemoryMonitor
    ): DeviceMonitor = DeviceMonitor(ctx, memoryMonitor)
}
