package org.alienz.heatermeter.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

class SamplesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application.applicationContext)

    fun recentSamples(): LiveData<List<Sample>> {
        return db.samples().recent()
    }
}