package io.github.blueinstruction.codehub.devtools.packages

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PackageInfo(
    val packageName: String,
    val displayName: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val isDebug: Boolean,
    val sourceDir: String?,
    val dataDir: String?,
    val minSdk: Int,
    val targetSdk: Int
)

@Singleton
class PackageInspector @Inject constructor(
    private val context: Context
) {
    fun list(): List<PackageInfo> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        return pm.getInstalledApplications(flags).map { app ->
            PackageInfo(
                packageName = app.packageName,
                displayName = pm.getApplicationLabel(app).toString(),
                versionName = runCatching { pm.getPackageInfo(app.packageName, 0).versionName }.getOrNull(),
                versionCode = runCatching { pm.getPackageInfo(app.packageName, 0).longVersionCode }.getOrDefault(0L),
                isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isDebug = (app.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                sourceDir = app.sourceDir,
                dataDir = app.dataDir,
                minSdk = app.minSdkVersion,
                targetSdk = app.targetSdkVersion
            )
        }.sortedBy { it.packageName }
    }

    fun find(query: String): List<PackageInfo> {
        val q = query.lowercase()
        return list().filter { it.packageName.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
    }

    fun get(packageName: String): PackageInfo? = list().firstOrNull { it.packageName == packageName }

    fun launch(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
