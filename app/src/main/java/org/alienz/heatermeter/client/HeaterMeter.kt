package org.alienz.heatermeter.client

import okhttp3.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.time.Instant

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

    @POST("/luci/admin/lm")
    suspend fun auth(@Body body: RequestBody): Response<String>
}