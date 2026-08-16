package codehub

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import codehub.service.CodeHubService

@HiltAndroidApp
class CodeHubApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannels()
    }

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICES,
                getString(R.string.channel_services),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Long-running development services"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BUILDS,
                getString(R.string.channel_builds),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Build progress and outcomes"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AGENTS,
                getString(R.string.channel_agents),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "AI agent activity and approvals"
            }
        )
    }

    companion object {
        const val CHANNEL_SERVICES = "codehub.services"
        const val CHANNEL_BUILDS = "codehub.builds"
        const val CHANNEL_AGENTS = "codehub.agents"

        fun startServices(app: Application) {
            CodeHubService.start(app)
        }
    }
}
