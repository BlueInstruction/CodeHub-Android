package codehub.ai.tools

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import codehub.ai.tools.impl.DeleteFileTool
import codehub.ai.tools.impl.ListDirectoryTool
import codehub.ai.tools.impl.ReadFileTool
import codehub.ai.tools.impl.RunCommandTool
import codehub.ai.tools.impl.SearchCodeTool
import codehub.ai.tools.impl.WriteFileTool
import javax.inject.Singleton
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentToolsModule {

    @Binds
    @IntoMap
    @StringKey("read_file")
    @Singleton
    abstract fun bindReadFile(impl: ReadFileTool): AgentTool

    @Binds
    @IntoMap
    @StringKey("write_file")
    @Singleton
    abstract fun bindWriteFile(impl: WriteFileTool): AgentTool

    @Binds
    @IntoMap
    @StringKey("delete_file")
    @Singleton
    abstract fun bindDeleteFile(impl: DeleteFileTool): AgentTool

    @Binds
    @IntoMap
    @StringKey("list_directory")
    @Singleton
    abstract fun bindListDirectory(impl: ListDirectoryTool): AgentTool

    @Binds
    @IntoMap
    @StringKey("search_code")
    @Singleton
    abstract fun bindSearchCode(impl: SearchCodeTool): AgentTool

    @Binds
    @IntoMap
    @StringKey("run_command")
    @Singleton
    abstract fun bindRunCommand(impl: RunCommandTool): AgentTool
}
