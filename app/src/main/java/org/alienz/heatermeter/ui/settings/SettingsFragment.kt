package org.alienz.heatermeter.ui.settings

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import org.alienz.heatermeter.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<EditTextPreference>(serverPassword)!!.apply {
            setSummaryProvider {
                if (text?.isNotEmpty() == true) "*".repeat(16) else "<blank>"
            }

            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
    }

    companion object {
        const val serverUrl = "server_url"
        const val serverPassword = "server_password"
    }
}