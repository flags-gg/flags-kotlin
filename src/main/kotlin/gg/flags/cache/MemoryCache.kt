package gg.flags.cache

import gg.flags.model.FeatureFlag
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class MemoryCache : Cache {
    private val flags = ConcurrentHashMap<String, FeatureFlag>()
    private val mutex = Mutex()
    private var lastRefresh: Instant? = null
    private var refreshInterval: Int = 60 // Default to 60 seconds

    override suspend fun get(name: String): Pair<Boolean, Boolean> {
        val flag = flags[name]
        return if (flag != null) {
            Pair(flag.enabled, true)
        } else {
            Pair(false, false)
        }
    }

    override suspend fun getAll(): List<FeatureFlag> {
        return flags.values.toList()
    }

    override suspend fun refresh(flags: List<FeatureFlag>, intervalAllowed: Int) {
        mutex.withLock {
            this.flags.clear()
            flags.forEach { flag ->
                this.flags[flag.details.name] = flag
            }
            this.refreshInterval = intervalAllowed
            this.lastRefresh = Instant.now()
        }
    }

    override suspend fun shouldRefreshCache(): Boolean {
        val lastRefreshTime = lastRefresh ?: return true
        val now = Instant.now()
        val elapsed = now.epochSecond - lastRefreshTime.epochSecond
        return elapsed >= refreshInterval
    }

    override suspend fun init() {
        // No initialization needed for memory cache
    }

    override suspend fun close() {
        // No cleanup needed for memory cache
    }
}