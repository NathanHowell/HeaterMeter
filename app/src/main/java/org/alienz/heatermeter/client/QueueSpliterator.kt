package org.alienz.heatermeter.client

import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.function.Consumer

internal class QueueSpliterator<T>(capacity: Int) : Spliterator<T>, AutoCloseable {
    private val queue = ArrayBlockingQueue<T?>(capacity, false)

    fun put(value: T) {
        queue.put(value)
    }

    override fun close() {
        queue.put(null)
    }

    override fun estimateSize(): Long {
        return Long.MAX_VALUE
    }

    override fun characteristics(): Int {
        return Spliterator.NONNULL or Spliterator.ORDERED
    }

    override fun tryAdvance(action: Consumer<in T>): Boolean {
        return when (val value = queue.take()) {
            null -> false
            else -> {
                action.accept(value)
                true
            }
        }
    }

    override fun trySplit(): Spliterator<T>? {
        return null
    }
}