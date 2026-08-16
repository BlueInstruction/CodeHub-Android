package codehub.workspace.template

interface WrapperAssets {
    fun gradlewScript(): ByteArray
    fun gradlewBatScript(): ByteArray
    fun gradleWrapperJar(): ByteArray
}
