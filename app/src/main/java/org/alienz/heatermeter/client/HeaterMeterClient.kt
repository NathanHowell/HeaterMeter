package org.alienz.heatermeter.client

import android.util.Log
import com.google.gson.Gson
import com.launchdarkly.eventsource.EventHandler
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import org.alienz.heatermeter.data.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.net.URL
import java.time.Instant
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
class HeaterMeterClient(private val baseUrl: URL) :
    CoroutineScope {
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient()
                .newBuilder()
                .followRedirects(false) // needed to get auth cookie
                .cookieJar(SysAuthCookieJar(baseUrl))
                .build())
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val hm = retrofit.create(HeaterMeter::class.java)
    private val gson = Gson()

    fun all(): Pair<ReceiveChannel<Event<Sample>>, ReceiveChannel<ProbeName>> {
        val (samples, names) = stream()

        val combiner = produce {
            history().consumeEach { send(it) }
            samples.consumeEach { send(it) }
        }

        return combiner to names
    }

    private suspend fun history(): ReceiveChannel<Event<Sample>> {
        val history = hm.history()
        val body = history.body()
        return parseHistory(body)
    }

    private fun parseHistory(history: String?): ReceiveChannel<Event<Sample>> = produce {
        if (history == null) {
            return@produce
        }

        send(Event.HistoryOpened())

        try {
            for (line in history.lines()) {
                val tokens = line.split(',')
                if (tokens.size == 7) {
                    val time = tokens[0].toLongOrNull() ?: Long.MIN_VALUE
                    val setPoint = tokens[1].toDoubleOrNull() ?: Double.NaN
                    val probes = tokens
                        .slice(2..5)
                        .mapIndexed { index, temperature ->
                            Probe(
                                index = index,
                                temperature = Temperature(
                                    temperature.toDoubleOrNull() ?: Double.NaN
                                ),
                                degreesPerHour = DegreesPerHour(
                                    Double.NaN
                                )
                            )
                        }
                    val fanSpeed = tokens[6].toDoubleOrNull() ?: Double.NaN

                    send(
                        Event.of(
                            Sample(
                                time = Instant.ofEpochSecond(time),
                                setPoint = Temperature(
                                    setPoint
                                ),
                                lidOpen = fanSpeed < 0 || fanSpeed.isNaN(),
                                fan = FanSpeed(
                                    fanSpeed
                                ),
                                probes = probes.toTypedArray()
                            )
                        )
                    )
                }
            }
        } finally {
            send(Event.HistoryClosed())
        }
    }

    private fun HeaterMeter.Event.Status.toSample(): Event<Sample> {
        return Event.of(
            Sample(
                time = Instant.ofEpochSecond(time),
                setPoint = Temperature(set),
                lidOpen = lid > 0,
                fan = FanSpeed(fan.c),
                probes = temps.mapIndexed { index, it ->
                    Probe(
                        index = index,
                        temperature = Temperature(
                            it.c
                        ),
                        degreesPerHour = DegreesPerHour(
                            it.dph
                        )
                    )
                }.toTypedArray()
            )
        )
    }

    private fun HeaterMeter.Event.Peaks.toSample(): Event<Sample> {
        throw IllegalArgumentException()
    }

    fun stream(): Pair<ReceiveChannel<Event<Sample>>, Channel<ProbeName>> {
        val samples = Channel<Event<Sample>>(16)
        val names = Channel<ProbeName>(16)

        val handler = object : EventHandler {
            override fun onOpen() {
                samples.sendBlocking(Event.StreamOpened<Sample>())
            }

            override fun onComment(comment: String?) {
                // ignored
            }

            override fun onMessage(event: String, messageEvent: MessageEvent) {
                when (event) {
                    "hmstatus" -> {
                        val status = gson.fromJson(
                            messageEvent.data,
                            HeaterMeter.Event.Status::class.java
                        )
                        samples.sendBlocking(status.toSample())
                        status.temps.forEachIndexed { index, temp ->
                            names.sendBlocking(ProbeName(index = index, name = temp.n))
                        }
                    }
//                    "peaks" ->
//                        gson.fromJson(
//                            messageEvent.data,
//                            HeaterMeter.Event.Peaks::class.java
//                        ).toSample()
                    "alarm" -> throw IllegalArgumentException() // admin/lm/alarm
                    else -> Log.d(
                        this::class.java.toString(),
                        "Ignoring event $event"
                    )
                }
            }

            override fun onClosed() {
                samples.sendBlocking<Event<Sample>>(
                    Event.StreamClosed()
                )
            }

            override fun onError(t: Throwable?) {
                Log.w(
                    this::class.java.toString(),
                    "SSE error: ${t?.message}",
                    t
                )
                // t?.apply { throw this }
            }
        }

        val eventSource = EventSource.Builder(
            handler,
            URL(baseUrl, "/luci/lm/stream").toURI()
        )
            .build()

        samples.invokeOnClose {
            eventSource.close()
            names.cancel()
        }

        eventSource.start()

        return samples to names
    }

    suspend fun login(username: String, password: String) {
        val response = hm.auth(
            FormBody.Builder()
                .add("luci_username", username)
                .add("luci_password", password)
                .build()
        )

        if (!response.isSuccessful && response.code() != 302) {
            throw IllegalStateException()
        }
    }

    override val coroutineContext: CoroutineContext
        get() = GlobalScope.coroutineContext + Dispatchers.IO
}