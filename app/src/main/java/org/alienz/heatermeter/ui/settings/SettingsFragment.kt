package org.alienz.heatermeter.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import org.alienz.heatermeter.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}