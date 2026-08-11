package net.typho.big_shot.loader.util

import java.util.function.BiConsumer
import java.util.function.Consumer

open class EventGraph<K : Any, T : Any> {
    inner class Event(
        @JvmField
        val id: K,
        @JvmField
        val event: T,
        @JvmField
        val runThisBefore: List<K>,
        @JvmField
        val runThisAfter: List<K>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EventGraph<*, *>.Event) return false

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    @JvmField
    protected val events = mutableListOf<Event>()
    @JvmField
    protected var resolved = true

    fun register(
        id: K,
        event: T
    ) {
        register(id, event, listOf(), listOf())
    }

    fun register(
        id: K,
        event: T,
        runThisBefore: List<K>,
        runThisAfter: List<K>
    ) {
        if (events.any { it.id == id }) {
            throw IllegalArgumentException("Registered duplicate event '$id'")
        }

        events.add(Event(id, event, runThisBefore, runThisAfter))
        resolved = false
    }

    fun execute(out: Consumer<T>) {
        resolve().forEach { out.accept(it.event) }
    }

    fun execute(out: BiConsumer<K, T>) {
        resolve().forEach { out.accept(it.id, it.event) }
    }

    fun resolve(): List<Event> {
        if (!resolved) {
            val lookup = events.associateBy { it.id }
            val edges = events.associateWith { mutableSetOf<Event>() }
            val incoming = events.associateWith { 0 }.toMutableMap()

            for (event in events) {
                for (before in event.runThisBefore) {
                    val target = lookup[before] ?: continue

                    if (edges[event]!!.add(target)) {
                        incoming[target] = incoming[target]!! + 1
                    }
                }

                for (after in event.runThisAfter) {
                    val source = lookup[after] ?: continue

                    if (edges[source]!!.add(event)) {
                        incoming[event] = incoming[event]!! + 1
                    }
                }
            }

            val queue = ArrayDeque(events.filter { incoming[it] == 0 })
            val result = mutableListOf<Event>()

            while (queue.isNotEmpty()) {
                val event = queue.removeFirst()
                result.add(event)

                for (next in edges[event]!!) {
                    incoming[next] = incoming[next]!! - 1

                    if (incoming[next] == 0) {
                        queue.addLast(next)
                    }
                }
            }

            if (result.size != events.size) {
                throw IllegalStateException("Event graph contains a cycle")
            }

            events.clear()
            events.addAll(result)

            resolved = true
        }

        return events
    }
}