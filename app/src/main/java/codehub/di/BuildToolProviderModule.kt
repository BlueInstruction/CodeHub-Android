package codehub.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import codehub.build.api.BuildTool
import codehub.build.api.BuildToolProvider
import codehub.build.gradle.GradleBuildProvider
import codehub.build.cmake.CMakeBuildProvider
import codehub.build.ninja.NinjaBuildProvider
import codehub.build.clang.ClangBuildProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BuildToolProviderModule {

    @Provides
    @Singleton
    fun provideBuildToolProviderMap(
        gradle: GradleBuildProvider,
        cmake: CMakeBuildProvider,
        ninja: NinjaBuildProvider,
        clang: ClangBuildProvider
    ): Map<BuildTool, @JvmSuppressWildcards BuildToolProvider> =
        mapOf(
            BuildTool.Gradle to gradle,
            BuildTool.CMake to cmake,
            BuildTool.Ninja to ninja,
            BuildTool.Clang to clang
        )
}
