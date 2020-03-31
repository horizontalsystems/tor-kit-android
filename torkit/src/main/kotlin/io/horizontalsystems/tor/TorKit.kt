package io.horizontalsystems.tor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.horizontalsystems.tor.core.TorConstants
import io.horizontalsystems.tor.utils.ConnectionManager
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import retrofit2.Retrofit
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

class TorKit(private val context: Context): TorManager.Listener {

    val torInfoSubject: PublishSubject<Tor.Info> = PublishSubject.create()
    private val torManager = TorManager(context, this)
    private var torService: TorService? = null
    private var torStarted = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TorService.LocalBinder
            torService = binder.getService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            torService = null
        }
    }

    val isNotificationEnabled: Boolean
        get() = when {
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> false
            else -> {
                val notificationChannel = NotificationManagerCompat.from(context).getNotificationChannel(
                    TorService.torNotificationChannelId)
                notificationChannel?.importance != NotificationManagerCompat.IMPORTANCE_NONE
            }
        }

    fun startTor(useBridges: Boolean){
        torStarted = true
        enableProxy()
        torManager.start(useBridges)
        startService(context)
    }

    fun stopTor(): Single<Boolean> {
        disableProxy()
        torService?.stop()
        torStarted = false
        return torManager.stop()
    }

    fun getSocketConnection(host: String, port: Int): Socket {
        return ConnectionManager.socks4aSocketConnection(
            host,
            port,
            false,//torManager.getTorInfo().isStarted,
            TorConstants.IP_LOCALHOST,
            TorConstants.SOCKS_PROXY_PORT_DEFAULT.toInt()
        )

    }

    fun getHttpConnection(url: URL): HttpURLConnection {

        return ConnectionManager.httpURLConnection(
            url,
            false,
            TorConstants.IP_LOCALHOST,
            TorConstants.HTTP_PROXY_PORT_DEFAULT.toInt()
        )
    }

    fun buildRetrofit(url: String, timeout: Long = 60): Retrofit {
        return ConnectionManager.retrofit(
            url,
            timeout,
            false,//torManager.getTorInfo().isStarted,
            TorConstants.IP_LOCALHOST,
            TorConstants.SOCKS_PROXY_PORT_DEFAULT.toInt()
        )
    }

    fun enableProxy() {
        ConnectionManager.setSystemProxy(
            true,
            TorConstants.IP_LOCALHOST,
            TorConstants.HTTP_PROXY_PORT_DEFAULT,
            TorConstants.SOCKS_PROXY_PORT_DEFAULT
        )
    }

    fun disableProxy() {
        ConnectionManager.disableSystemProxy()
    }

    override fun statusUpdate(torInfo: Tor.Info) {
        torInfoSubject.onNext(torInfo)
        if (torStarted) {
            torService?.updateNotification(torInfo)
        }
    }

    private fun startService(context: Context) {
        val serviceIntent = Intent(context, TorService::class.java)
        serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android")
        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        ContextCompat.startForegroundService(context, serviceIntent)
    }

}
