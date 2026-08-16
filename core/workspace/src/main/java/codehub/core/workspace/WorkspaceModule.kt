package codehub.core.workspace

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import codehub.core.workspace.fs.JavaNioFileSystemGateway
import codehub.core.workspace.repo.RoomWorkspaceRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkspaceModule {

    @Binds
    @Singleton
    abstract fun bindWorkspaceRepository(impl: RoomWorkspaceRepository): WorkspaceRepository

    @Binds
    @Singleton
    abstract fun bindFileSystemGateway(impl: JavaNioFileSystemGateway): FileSystemGateway
}
