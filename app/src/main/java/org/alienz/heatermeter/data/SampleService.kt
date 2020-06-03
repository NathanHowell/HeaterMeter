package org.alienz.heatermeter.data

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SampleService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? = null
}
