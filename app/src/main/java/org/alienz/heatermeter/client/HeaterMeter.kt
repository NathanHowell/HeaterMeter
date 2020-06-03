package org.alienz.heatermeter.client

import com.google.gson.Gson
import com.launchdarkly.eventsource.EventHandler
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.lang.IllegalArgumentException
import java.net.URI
import java.time.Instant
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.stream.Stream
import java.util.stream.StreamSupport

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

interface HeaterMeter2 {
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
            val temps: Array<Temp>): Event() {
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

        data class Peaks(val foo: Int): Event()
    }

    @GET("/luci/lm/hist")
    fun history(): CompletableFuture<Response<String>>

    @GET("/luci/lm/hmstatus")
    fun status(): CompletableFuture<Response<Event.Status>>

    @GET("/luci/admin/lm")
    fun auth(): Call<String>
}

object HeaterMeterClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.1.111")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val hm = retrofit.create(HeaterMeter2::class.java)
    private val gson = Gson()

    fun all(): Stream<Sample> {
        return Stream.concat(history(), stream())
    }

    fun history(): Stream<Sample> {
        val spliterator = QueueSpliterator<String>(1)
        hm.history().thenAccept {
            val body = it.body()
            if (it.isSuccessful && body != null) {
                spliterator.put(body)
            }
        }

        return StreamSupport.stream(spliterator, false)
            .flatMap { parseHistory(it) }
    }

    private fun parseHistory(history: String): Stream<Sample> {
        return history.lines().mapNotNull { history ->
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
                            temperature = Temperature(temperature.toDoubleOrNull() ?: Double.NaN),
                            degreesPerHour = DegreesPerHour(Double.NaN)
                        )
                    }
                val fanSpeed = tokens[6].toDoubleOrNull() ?: Double.NaN

                Sample(
                    time = Instant.ofEpochSecond(time),
                    setPoint = Temperature(setPoint),
                    lidOpen = fanSpeed < 0 || fanSpeed.isNaN(),
                    fan = FanSpeed(fanSpeed),
                    probes = probes.toTypedArray()
                )
            } else {
                null
            }
        }.stream()
    }


    fun stream(): Stream<Sample> {
        val spliterator = QueueSpliterator<HeaterMeter2.Event>(16)

        val handler = object : EventHandler {
            override fun onOpen() {
            }

            override fun onComment(comment: String?) {
            }

            override fun onMessage(event: String, messageEvent: MessageEvent) {
                val payload = when (event) {
                    "hmstatus" -> gson.fromJson(messageEvent.data, HeaterMeter2.Event.Status::class.java)
                    "peaks" -> gson.fromJson(messageEvent.data, HeaterMeter2.Event.Peaks::class.java)
                    "log" -> throw IllegalArgumentException()
                    "alarm" -> throw IllegalArgumentException() // admin/lm/alarm
                    "error" -> throw IllegalArgumentException()
                    "pidint" -> throw IllegalArgumentException()
                    else -> throw IllegalArgumentException()
                }

                spliterator.put(payload)
            }

            override fun onClosed() {
            }

            override fun onError(t: Throwable?) {
                t?.apply { throw this }
            }
        }

        val eventSource = EventSource.Builder(handler, URI("http://10.0.1.111/luci/lm/stream"))
            .build()

        val stream = StreamSupport.stream(spliterator, false)

        eventSource.start()

        return stream.flatMap {
            when (it) {
                is HeaterMeter2.Event.Status ->
                    Stream.of(
                        Sample(
                            time = Instant.ofEpochSecond(it.time),
                            setPoint = Temperature(it.set),
                            lidOpen = it.lid > 0.0,
                            fan = FanSpeed(it.fan.c),
                            probes = it.temps
                                .mapIndexed { index, x ->
                                    Probe(
                                        name = ProbeName(x.n),
                                        index = index,
                                        temperature = Temperature(x.c),
                                        degreesPerHour = DegreesPerHour(x.dph)
                                    )
                                }
                                .toTypedArray()
                        )
                    )
                else -> Stream.of()
            }
        }
    }
}