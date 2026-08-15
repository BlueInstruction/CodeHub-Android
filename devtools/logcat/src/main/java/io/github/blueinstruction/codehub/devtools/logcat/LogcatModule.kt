package io.github.blueinstruction.codehub.devtools.logcat

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LogcatModule {

    @Binds
    @Singleton
    abstract fun bindLogcatService(impl: CliLogcatService): LogcatService
}
