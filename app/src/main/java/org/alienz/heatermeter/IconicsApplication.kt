package org.alienz.heatermeter

import android.app.Application
import com.mikepenz.iconics.Iconics
import com.mikepenz.iconics.typeface.library.ionicons.Ionicons

class IconicsApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Iconics.init(applicationContext)
        Iconics.registerFont(Ionicons)
    }
}