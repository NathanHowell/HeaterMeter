package org.alienz.heatermeter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.mikepenz.iconics.Iconics
import com.mikepenz.iconics.typeface.library.ionicons.Ionicons
import org.alienz.heatermeter.ui.ChartFragment
import org.alienz.heatermeter.ui.GaugeFragment
import org.alienz.heatermeter.ui.settings.SettingsFragment
import java.lang.IllegalArgumentException

class MainActivity : AppCompatActivity() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity)

        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.pager)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> ChartFragment()
                    1 -> GaugeFragment()
                    2 -> SettingsFragment()
                    else -> throw IllegalArgumentException()
                }
            }
        }

        val oldTabs = (0 until tabLayout.tabCount)
            .map { tabLayout.getTabAt(it) }
            .map {
                object {
                    val text = it?.text
                    val icon = it?.icon
                }
            }
            .toTypedArray()

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            val oldTab = oldTabs[position]
            tab.text = oldTab.text
            tab.icon = oldTab.icon
        }.attach()
    }

    override fun onStart() {
        super.onStart()
    }
}