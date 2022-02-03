package io.horizontalsystems.tor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class TorNotificationManager(private val context: Context) {

    private val androidNotificationManager = NotificationManagerCompat.from(context)

    val isNotificationEnabled: Boolean
        get() = when {
            !androidNotificationManager.areNotificationsEnabled() -> false
            else -> {
                val notificationChannel =
                    androidNotificationManager.getNotificationChannel(torNotificationChannelId)
                notificationChannel?.importance != NotificationManagerCompat.IMPORTANCE_NONE
            }
        }

    fun showNotification(torInfo: Tor.Info? = null) {
        createNotificationChannel(context, "Tor Channel", torNotificationChannelId)

        val notification = getNotification(torInfo)
        androidNotificationManager.notify(notificationId, notification)
    }

    fun removeNotification() {
        androidNotificationManager.cancel(notificationId)
    }

    private fun getNotification(torInfo: Tor.Info?): Notification {
        var title = "Tor: Starting"
        var icon = iconConnecting
        var content = "Connecting ..."

        torInfo?.let {
            title = when (it.status) {
                EntityStatus.STARTING -> "Tor: Starting"
                EntityStatus.RUNNING -> "Tor: Running"
                else -> "Tor: Stopped"
            }

            icon = when (it.connection.status) {
                ConnectionStatus.CONNECTING -> iconConnecting
                ConnectionStatus.CONNECTED -> iconConnected
                else -> iconError
            }

            content = when (it.connection.status) {
                ConnectionStatus.CONNECTING -> "Connecting ..."
                ConnectionStatus.CONNECTED -> "Successfully Connected!"
                else -> "Disconnected"
            }
        }

        return NotificationCompat.Builder(context, torNotificationChannelId)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(getPendingIntent())
            .setSmallIcon(icon)
            .build()
    }

    private fun getPendingIntent(): PendingIntent? {
        val launchIntent: Intent =
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null

        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel(ctx: Context, appName: String, channelId: String) {

        val androidNotificationManager = NotificationManagerCompat.from(ctx)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, appName, importance)

        androidNotificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val notificationId = 100
        const val torNotificationChannelId = "torNotificationChannelId"

        private val iconConnecting = R.drawable.ic_tor
        private val iconConnected = R.drawable.ic_tor_running
        private val iconError = R.drawable.ic_tor_error
    }
}
