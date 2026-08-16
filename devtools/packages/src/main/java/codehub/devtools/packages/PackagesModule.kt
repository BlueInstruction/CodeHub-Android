package codehub.devtools.packages

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PackagesModule {

    @Provides
    @Singleton
    fun providePackageInspector(@ApplicationContext ctx: android.content.Context): PackageInspector =
        PackageInspector(ctx)
}
