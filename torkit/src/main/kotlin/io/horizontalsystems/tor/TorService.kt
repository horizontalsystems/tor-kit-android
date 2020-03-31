package io.horizontalsystems.tor

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.system.exitProcess


class TorService : Service() {

    private val iconConnecting = R.drawable.ic_tor
    private val iconConnected = R.drawable.ic_tor_running
    private val iconError = R.drawable.ic_tor_error
    private val binder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel(this, "Tor Connection Channel",
            torNotificationChannelId
        )

        val notification = getNotification()
        startForeground(notificationId, notification)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stop()
    }

    @SuppressLint("CheckResult")
    fun stop() {
        TorManager.instance.stop().subscribe(
            {
                // Close notifications and exit App
                stopForeground(true)
                exitProcess(0)
            }, {
                //Exit app on Error
                exitProcess(0)
            })
    }

    fun updateNotification(torInfo: Tor.Info?) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, getNotification(torInfo))
    }

    private fun getNotification(torInfo: Tor.Info? = null): Notification {
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

        return NotificationCompat.Builder(this,
            torNotificationChannelId
        )
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(getPendingIntent())
            .setSmallIcon(icon)
            .build()
    }

    private fun getPendingIntent(): PendingIntent? {
        val launchIntent: Intent =
            packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return PendingIntent.getActivity(this, 0, launchIntent, 0)
    }

    private fun createNotificationChannel(ctx: Context, appName: String, channelId: String) {

        val androidNotificationManager = NotificationManagerCompat.from(ctx)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, appName, importance)

        androidNotificationManager.createNotificationChannel(channel)
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): TorService = this@TorService
    }

    companion object {
        const val notificationId = 100
        const val torNotificationChannelId = "torNotificationChannelId"
    }
}
