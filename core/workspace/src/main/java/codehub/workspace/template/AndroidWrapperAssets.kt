package codehub.workspace.template

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidWrapperAssets @Inject constructor(
    private val context: Context
) : WrapperAssets {

    override fun gradlewScript(): ByteArray =
        context.assets.open("gradle-wrapper/gradlew").use { it.readBytes() }

    override fun gradlewBatScript(): ByteArray =
        context.assets.open("gradle-wrapper/gradlew.bat").use { it.readBytes() }

    override fun gradleWrapperJar(): ByteArray =
        context.assets.open("gradle-wrapper/gradle-wrapper.jar").use { it.readBytes() }
}
