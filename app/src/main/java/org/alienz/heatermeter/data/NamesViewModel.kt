package org.alienz.heatermeter.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

class NamesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application.applicationContext)

    fun names(): LiveData<List<ProbeName>> {
        return db.names().all()
    }
}