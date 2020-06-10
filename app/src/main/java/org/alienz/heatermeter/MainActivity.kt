package org.alienz.heatermeter

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.alienz.heatermeter.data.SampleService
import org.alienz.heatermeter.ui.AlarmFragment
import org.alienz.heatermeter.ui.ChartFragment
import org.alienz.heatermeter.ui.GaugeFragment

class MainActivity : AppCompatActivity() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity)

//        tabLayout = findViewById(R.id.tabs)
//        viewPager = findViewById(R.id.pager)
//
//        viewPager.adapter = object : FragmentStateAdapter(this) {
//            override fun getItemCount(): Int = 3
//
//            override fun createFragment(position: Int): Fragment {
//                return when (position) {
//                    0 -> ChartFragment()
//                    1 -> GaugeFragment()
//                    2 -> AlarmFragment()
//                    else -> throw IllegalArgumentException()
//                }
//            }
//        }
//
//        val oldTabs = (0 until tabLayout.tabCount)
//            .map { tabLayout.getTabAt(it) }
//            .map {
//                object {
//                    val text = it?.text
//                    val icon = it?.icon
//                }
//            }
//            .toTypedArray()
//
//        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
//            val oldTab = oldTabs[position]
//            tab.text = oldTab.text
//            tab.icon = oldTab.icon
//        }.attach()
    }

    override fun onStart() {
        val serviceIntent = Intent(applicationContext, SampleService::class.java)

        ContextCompat.startForegroundService(applicationContext, serviceIntent)

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