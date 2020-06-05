package org.alienz.heatermeter.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consume
import org.alienz.heatermeter.client.Event
import org.alienz.heatermeter.client.HeaterMeterClient
import org.alienz.heatermeter.ui.settings.SettingsFragment
import java.net.URL
import kotlin.coroutines.CoroutineContext

class SampleService : Service(), CoroutineScope, SharedPreferences.OnSharedPreferenceChangeListener {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(this@SampleService::class.qualifiedName, "Failed: ${t.message}", t)
        GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(this@SampleService.applicationContext, t.message, Toast.LENGTH_SHORT)
                .show()
        }
    }

    @ExperimentalCoroutinesApi
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val notifications = NotificationManagerCompat.from(applicationContext)
        val notificationChannel = NotificationChannel("id", "name", NotificationManager.IMPORTANCE_LOW)
        notifications.createNotificationChannel(notificationChannel)
        val notification = Notification
            .Builder(this, notificationChannel.id)
            .setContentText("HeaterMeter is syncing")
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)

        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        preferences.registerOnSharedPreferenceChangeListener(this)

        Log.i(this::class.java.toString(), "Initial start")
        restartEvents(PreferenceManager.getDefaultSharedPreferences(applicationContext))

        return START_STICKY
    }

    @ExperimentalCoroutinesApi
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            SettingsFragment.serverUrl -> {
                Log.i(this::class.java.toString(), "Restarting on key change: $key")
                restartEvents(sharedPreferences)
            }
        }
    }

    @ExperimentalCoroutinesApi
    private fun restartEvents(sharedPreferences: SharedPreferences) {
        synchronized(jobLock) {
            currentJob?.cancel()
            currentJob = launch { streamToDb(sharedPreferences) }
        }
    }

    private var currentJob: Job? = null
    private val jobLock = object {}

    @ExperimentalCoroutinesApi
    suspend fun streamToDb(sharedPreferences: SharedPreferences) {
        val serverUrl = sharedPreferences.getString(SettingsFragment.serverUrl, null) ?: return

        val db = AppDatabase.getInstance(applicationContext)
        val samplesDao = db.samples()
        val namesDao = db.names()

        withContext(coroutineExceptionHandler + Dispatchers.IO) {
            val serverUrl = URL(serverUrl)
            val client = HeaterMeterClient(serverUrl)
            val (samples, names) = client.all()

            launch {
                samples.consume {
                    while (true) {
                        when (val e = receive()) {
                            is Event.Sample -> {
                                Log.i("Sample", "received $e")
                                samplesDao.insert(e.value)
                            }
                        }
                    }
                }
            }

            launch {
                names.consume {
                    val seen = mutableMapOf<Int, String?>()
                    while (true) {
                        val e = receive()
                        if (seen[e.index] != e.name) {
                            Log.i("ProbeName", "received $e")
                            namesDao.insert()
                            seen[e.index] = e.name
                        }
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + coroutineExceptionHandler
}
