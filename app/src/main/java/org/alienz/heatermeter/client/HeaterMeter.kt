package org.alienz.heatermeter.client

import android.util.Log
import com.google.gson.Gson
import com.launchdarkly.eventsource.EventHandler
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.lang.IllegalArgumentException
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.coroutines.CoroutineContext

sealed class Event<T> {
    data class Sample<T>(val value: T) : Event<T>()

    class HistoryOpened<T> : Event<T>()
    class HistoryClosed<T> : Event<T>()
    class StreamOpened<T> : Event<T>()
    class StreamClosed<T> : Event<T>()

    fun <R> map(fn: (T) -> R): Event<R> = when (this) {
        is Sample -> Sample(fn(this.value))
        is HistoryOpened -> HistoryOpened()
        is HistoryClosed -> HistoryClosed()
        is StreamOpened -> StreamOpened()
        is StreamClosed -> StreamClosed()
    }

    companion object {
        fun <T> of(value: T): Sample<T> = Sample(value)
    }
}

inline class Temperature(val degrees: Double)

inline class DegreesPerHour(val dph: Double)

inline class FanSpeed(val rpm: Double)

inline class ProbeName(val name: String) {
    companion object {
        val names = (0..3).map { ProbeName("Probe $it") }
    }
}

data class Probe(
    val name: ProbeName,
    val index: Int,
    val temperature: Temperature,
    val degreesPerHour: DegreesPerHour
)

data class Sample(
    val time: Instant,
    val setPoint: Temperature,
    val lidOpen: Boolean,
    val fan: FanSpeed,
    val probes: Array<Probe>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Sample

        if (time != other.time) return false
        if (setPoint != other.setPoint) return false
        if (lidOpen != other.lidOpen) return false
        if (fan != other.fan) return false
        if (!probes.contentEquals(other.probes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = time.hashCode()
        result = 31 * result + setPoint.hashCode()
        result = 31 * result + lidOpen.hashCode()
        result = 31 * result + fan.hashCode()
        result = 31 * result + probes.contentHashCode()
        return result
    }
}

interface HeaterMeter {
    data class Fan(val c: Double) // , val a: Int, val f: Int)

    data class Temp(val n: String, val c: Double, val dph: Double, val a: TempA)

    data class TempA(val l: Int, val h: Int, val r: Any?)

    sealed class Event {
        data class Status(
            val time: Long,
            val `set`: Double,
            val lid: Double,
            val fan: Fan,
            val adc: Array<Int>,
            val temps: Array<Temp>
        ) : Event() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Status

                if (time != other.time) return false
                if (`set` != other.`set`) return false
                if (lid != other.lid) return false
                if (fan != other.fan) return false
                if (!adc.contentEquals(other.adc)) return false
                if (!temps.contentEquals(other.temps)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = time.hashCode()
                result = 31 * result + `set`.hashCode()
                result = 31 * result + lid.hashCode()
                result = 31 * result + fan.hashCode()
                result = 31 * result + adc.contentHashCode()
                result = 31 * result + temps.contentHashCode()
                return result
            }
        }

        data class Peaks(val foo: Int) : Event()
    }

    @GET("/luci/lm/hist")
    suspend fun history(): Response<String>

    @GET("/luci/lm/hmstatus")
    suspend fun status(): Response<Response<Event.Status>>

    @GET("/luci/admin/lm")
    suspend fun auth(): Call<String>
}

@ExperimentalCoroutinesApi
class HeaterMeterClient(private val baseUrl: URL) : CoroutineScope {
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val hm = retrofit.create(HeaterMeter::class.java)
    private val gson = Gson()

    fun all(): ReceiveChannel<Event<Sample>> = produce {
        history().consumeEach { send(it) }
        stream().consumeEach { send(it) }
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
                val tokens = history.split(',')
                if (tokens.size == 7) {
                    val time = tokens[0].toLongOrNull() ?: Long.MIN_VALUE
                    val setPoint = tokens[1].toDoubleOrNull() ?: Double.NaN
                    val probes = tokens
                        .slice(2..5)
                        .mapIndexed { index, temperature ->
                            Probe(
                                name = ProbeName.names[index],
                                index = index,
                                temperature = Temperature(
                                    temperature.toDoubleOrNull() ?: Double.NaN
                                ),
                                degreesPerHour = DegreesPerHour(Double.NaN)
                            )
                        }
                    val fanSpeed = tokens[6].toDoubleOrNull() ?: Double.NaN

                    send(
                        Event.of(
                            Sample(
                                time = Instant.ofEpochSecond(time),
                                setPoint = Temperature(setPoint),
                                lidOpen = fanSpeed < 0 || fanSpeed.isNaN(),
                                fan = FanSpeed(fanSpeed),
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
        throw IllegalArgumentException()
    }

    private fun HeaterMeter.Event.Peaks.toSample(): Event<Sample> {
        throw IllegalArgumentException()
    }

    fun stream(): ReceiveChannel<Event<Sample>> = produce<Event<Sample>> {
        val handler = object : EventHandler {
            override fun onOpen() {
                channel.sendBlocking<Event<Sample>>(Event.StreamOpened<Sample>())
            }

            override fun onComment(comment: String?) {
                // ignored
            }

            override fun onMessage(event: String, messageEvent: MessageEvent) {
                val payload = when (event) {
                    "hmstatus" ->
                        gson.fromJson(
                            messageEvent.data,
                            HeaterMeter.Event.Status::class.java
                        ).toSample()
                    "peaks" ->
                        gson.fromJson(
                            messageEvent.data,
                            HeaterMeter.Event.Peaks::class.java
                        ).toSample()
                    "log" -> throw IllegalArgumentException()
                    "alarm" -> throw IllegalArgumentException() // admin/lm/alarm
                    "error" -> throw IllegalArgumentException()
                    "pidint" -> throw IllegalArgumentException()
                    else -> throw IllegalArgumentException()
                }

                channel.sendBlocking(payload)
            }

            override fun onClosed() {
                channel.sendBlocking<Event<Sample>>(Event.StreamClosed())
            }

            override fun onError(t: Throwable?) {
                t?.apply { throw this }
            }
        }

        val eventSource = EventSource.Builder(handler, URL(baseUrl, "/luci/lm/stream").toURI())
            .build()

        eventSource.start()
    }

    override val coroutineContext: CoroutineContext
        get() = GlobalScope.coroutineContext + Dispatchers.IO
}
