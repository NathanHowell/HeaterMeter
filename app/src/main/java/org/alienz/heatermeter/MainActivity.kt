package org.alienz.heatermeter

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.alienz.heatermeter.data.CompactingWorker
import org.alienz.heatermeter.data.SampleService
import org.alienz.heatermeter.ui.AlarmFragment
import org.alienz.heatermeter.ui.ChartFragment
import org.alienz.heatermeter.ui.GaugeFragment
import java.time.Duration

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity)
    }

    override fun onStart() {
        val serviceIntent = Intent(applicationContext, SampleService::class.java)

        ContextCompat.startForegroundService(applicationContext, serviceIntent)

        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                "Compactor",
                ExistingPeriodicWorkPolicy.REPLACE,
                PeriodicWorkRequest
                    .Builder(CompactingWorker::class.java, Duration.ofHours(1L))
                    .setConstraints(Constraints
                        .Builder()
                        .setRequiresBatteryNotLow(true)
                        .build())
                    .build())

        super.onStart()
    }

    override fun onStop() {
        val serviceIntent = Intent(applicationContext, SampleService::class.java)

        stopService(serviceIntent)

        super.onStop()
    }

    fun startSettingsActivity(view: View) {
        val intent = Intent(applicationContext, SettingsActivity::class.java)

        startActivity(intent)
    }
}