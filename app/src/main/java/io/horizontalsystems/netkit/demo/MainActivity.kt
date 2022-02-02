package io.horizontalsystems.netkit.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.MalformedURLException
import java.net.URL
import kotlin.system.exitProcess
import io.horizontalsystems.tor.TorKit


@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {


    interface GetIPApi {
        @GET
        @Headers("Content-Type: text/plain")
        fun getIP(@Url path: String): Flowable<String>
    }

    private lateinit var torKit: TorKit
    private val listItems = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private val disposables = CompositeDisposable()
    private var torStarted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //------------Init NetKit -------------------
        torKit = TorKit(context = applicationContext)
        //-------------------------------------------

        findViewById<Button>(R.id.btnTor)?.setOnClickListener {
            if (!torStarted) {
                startTor()
                torStarted = true
                findViewById<Button>(R.id.btnTor)?.text = "Stop Tor"
            } else {
                stopTor()
                torStarted = false
                findViewById<Button>(R.id.btnTor)?.text = "Start Tor"
            }
        }

        findViewById<Button>(R.id.btnTorTest).setOnClickListener {
            testTORConnection()
        }

        findViewById<Button>(R.id.btnRestartApp)?.setOnClickListener {
            finishAffinity()
            startActivity(Intent(applicationContext, MainActivity::class.java))
            exitProcess(0)
        }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listItems)
        findViewById<ListView>(R.id.statusView)?.adapter = adapter
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    private fun logEvent(logMessage: String? = "") {

        logMessage?.let {
            listItems.add(logMessage)
            adapter.notifyDataSetChanged()
        }
    }

    private fun stopTor() {
        disposables.add(
            torKit.stopTor()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { torStopped ->
                        logEvent("Tor stopped:${torStopped}")
                    },
                    { error ->
                        logEvent("TorError:${error}")
                    })
        )

    }

    private fun startTor() {
        disposables.add(
            torKit.torInfoSubject.observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { netStatus ->
                        netStatus.statusMessage?.let {
                            logEvent("${netStatus.statusMessage}")
                        }
                    },
                    { error ->
                        logEvent("TorError:${error}")
                    })
        )
        torKit.startTor(useBridges = false)
    }

    private fun testTORConnection() {

        findViewById<TextView>(R.id.txTorTestStatus).text = "Getting IP Address ... "
        findViewById<TextView>(R.id.txTorTestStatus2).text = "Checking socket connection ... "

        getIP()
        // Last IP 185.220.101.29
    }

    private fun getIP() {

        val checkIPUrl = "https://api.ipify.org"
        val checkIPApiURL =
            "http://api.ipapi.com/api/check?access_key=d786e103047a3510ba0d8bb0d9a92b02&fields=ip"


        object : Thread() {
            override fun run() {

                //doHTTPURLConnection(checkIPUrl)
                doOkHttp3(checkIPUrl)
                //doURL(checkIPUrl)
                doRetrofitClient(checkIPUrl)
                doSocketConnection(checkIPApiURL)
            }
        }.start()
    }

    fun doOkHttp3(url: String) {
        val client = OkHttpClient()
        val request: Request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute()
            .use { response ->
                findViewById<TextView>(R.id.txTorTestStatus).text = response.body?.charStream()!!.readText()
            }

    }

    fun doHTTPURLConnection(url: String) {

        val urlConnection = torKit.getHttpConnection(URL(url))

        try {
            val inStream = urlConnection.inputStream
            val isw = InputStreamReader(inStream)

            var data: Int = isw.read()
            var output = ""

            while (data != -1) {
                val current = data.toChar()
                data = isw.read()
                output += current
            }
            findViewById<TextView>(R.id.txTorTestStatus).text = "IP assigned :$output"

        } catch (e: Exception) {
            findViewById<TextView>(R.id.txTorTestStatus).text = e.toString()
        } finally {
            urlConnection.disconnect()
        }

        findViewById<TextView>(R.id.txTorTestStatus2).text = "Getting IP from RetroFit :"

    }

    fun doRetrofitClient(url: String) {
        val retroFitClient = torKit.buildRetrofit(url)
        val obser = retroFitClient.create(GetIPApi::class.java)

        disposables.add(
            obser.getIP("/").subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result -> findViewById<TextView>(R.id.txTorTestStatus2).text = "IP assigned :$result" },
                    { error ->
                        findViewById<TextView>(R.id.txTorTestStatus2).text = error.toString()
                    })
        )
    }

    fun doSocketConnection(sUrl: String) {
        try {

            val url: URL = try {
                URL(sUrl)
            } catch (ex: MalformedURLException) {
                return
            }

            val hostname = url.host
            val port = 80

            findViewById<TextView>(R.id.txTorTestStatus3).text = "Getting IP from socket connection"
            val socket = torKit.getSocketConnection(hostname, port)

            val output = socket.getOutputStream()
            val writer = PrintWriter(output, true)

            //writer.println("HEAD " + url.path + " HTTP/1.1")
            writer.println("GET " + url.getFile() + " HTTP/1.1")
            writer.println("Host: $hostname")
            writer.println("User-Agent: Simple Http Client")
            writer.println("Accept: text/plain")
            writer.println("Accept-Language: en-US")
            writer.println("Connection: close")
            writer.println()

            val input = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(input))

            var line: String

            while (reader.readLine().also { line = it } != null) {
                if (line.contains("{\"ip")) {
                    findViewById<TextView>(R.id.txTorTestStatus3).text = line
                    break
                }
            }

            output.close()
            input.close()
            socket.close()

        } catch (e: Exception) {
            findViewById<TextView>(R.id.txTorTestStatus3).text = e.toString()
        } finally {
        }
    }

    fun doURL(uri: String) {

        var i = 0
        var c: Char

        return URL(uri)
            .openConnection()
            .apply {
                connectTimeout = 5000
                readTimeout = 60000
                setRequestProperty("Accept", "text/plain")
            }
            .getInputStream()
            .use {

                var out: String = ""
                while (it.read().also({ i = it }) !== -1) {
                    c = i.toChar()
                    // prints character
                    out = out + c
                }
                findViewById<TextView>(R.id.txTorTestStatus2).text = out

            }
    }
}
