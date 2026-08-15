package io.github.blueinstruction.codehub.core.permissions

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionsModule {
    @Binds
    @Singleton
    abstract fun bindPermissionDecider(impl: DefaultPermissionDecider): PermissionDecider
}
