package org.alienz.heatermeter.data

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
import org.alienz.heatermeter.client.Event
import org.alienz.heatermeter.client.HeaterMeterClient
import org.alienz.heatermeter.client.Sample
import org.alienz.heatermeter.ui.settings.SettingsFragment
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

@ExperimentalCoroutinesApi
class HeaterMeterViewModel(private val sharedPreferences: SharedPreferences
) : ViewModel() {
    private val events: AtomicReference<ReceiveChannel<Event<Sample>>?> = AtomicReference()

    suspend fun restart(): ReceiveChannel<Event<Sample>> {
        return withContext(viewModelScope.coroutineContext + Dispatchers.IO) {
            val serverUrl = URL(sharedPreferences.getString(SettingsFragment.serverUrl, null))
            val client = HeaterMeterClient(serverUrl)
            val stream = client.all()
            events.getAndSet(stream)?.cancel()
            stream
        }
    }
}