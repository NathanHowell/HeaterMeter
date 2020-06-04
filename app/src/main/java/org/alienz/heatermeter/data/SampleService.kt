package org.alienz.heatermeter.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import org.alienz.heatermeter.client.Event
import org.alienz.heatermeter.client.Sample
import org.alienz.heatermeter.ui.settings.SettingsFragment
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

class SampleService : Service(), CoroutineScope, SharedPreferences.OnSharedPreferenceChangeListener {
    private var events: AtomicReference<ReceiveChannel<Event<Sample>>?> = AtomicReference()
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

        startForeground(1, notification)

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
    private fun restartEvents(sharedPreferences: SharedPreferences): Job = launch {
        val stream = HeaterMeterViewModel(sharedPreferences).restart()
        events.getAndSet(stream)?.cancel()

        for (foo in stream) {
            println(foo)
        }
//
//        launch(Dispatchers.Main) {
//
//        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + coroutineExceptionHandler
}
