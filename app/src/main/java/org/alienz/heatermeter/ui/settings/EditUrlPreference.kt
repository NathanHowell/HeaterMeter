package org.alienz.heatermeter.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import java.net.MalformedURLException
import java.net.URL

class EditUrlPreference(context: Context?, attrs: AttributeSet) : EditTextPreference(context, attrs) {
    init {
        setOnBindEditTextListener { it.inputType = InputType.TYPE_TEXT_VARIATION_URI }
    }

    override fun setText(text: String?) {
        for (prefix in arrayOf("", "http://")) {
            try {
                return super.setText(URL("${prefix}${text}").toString())
            } catch (_: MalformedURLException) {
                // yeah, we're validating URLs with exceptions...
            }
        }
    }

    override fun getSummary(): CharSequence {
        return super.getText()
    }
}