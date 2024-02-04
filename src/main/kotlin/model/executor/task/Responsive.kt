package model.executor.task

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap


class Responsive<T>(private val callbacks: MutableList<Callback<T>>) {
    constructor() : this(mutableListOf())

    fun addCallback(callback: Callback<T>) {
        Callback.addCallback(callback, callbacks)
    }

}

abstract class Callback<T>(val priority: Int = 0, val permanent: Boolean = false) {
    abstract fun call(data: T)

    companion object {
        fun <T> addCallback(callback: Callback<T>, callbacks: List<MutableList<Callback<T>>>) {
            for (c in callbacks) {
                addCallback(callback, c)
            }
        }

        fun <T> addCallback(callback: Callback<T>, callbacks: MutableList<Callback<T>>) {
            callbacks.add(callback)
            callbacks.sortBy { it.priority }
        }
    }
}

@Serializable
open class StatusResponsive(val name: String = "") {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)

    @Transient
    val stateCallbacks: ConcurrentHashMap<Status, MutableList<Callback<Status>>> = ConcurrentHashMap()

    @Transient
    val permanentStateCallbacks: ConcurrentHashMap<Status, MutableList<Callback<Status>>> = ConcurrentHashMap()

    @Transient
    val mutex: Mutex = Mutex()

    init {
        for (s in Status.entries) {
            stateCallbacks[s] = mutableListOf()
            permanentStateCallbacks[s] = mutableListOf()
        }
    }


    suspend fun addStatusCallback(callback: Callback<Status>, vararg status: Status) {
        mutex.withLock {
            if (callback.permanent) {
                Callback.addCallback(callback, status.map { permanentStateCallbacks[it]!! })
            } else {
                Callback.addCallback(callback, status.map { stateCallbacks[it]!! })
            }
        }
    }


    fun statusChanged(status: Status) {
        if (status == Status.inited) {
            return
        }

        // 因为通常是先准备好了回调，然后才会触发状态变化，所以这里不需要加锁
        val callbacks = stateCallbacks.remove(status) ?: mutableListOf()
        if (callbacks.isNotEmpty()) {
            logger.info("${this.name} status change to $status")
        }

        val permanentCallbacks = permanentStateCallbacks[status]!!
        for (callback in permanentCallbacks) {
            Callback.addCallback(callback, callbacks)
        }

        for (callback in callbacks) {
            try {
                callback.call(status)
            } catch (e: Exception) {
                logger.error(" ${this.name} status change to $status callback error", e)
            }
        }
    }

}









