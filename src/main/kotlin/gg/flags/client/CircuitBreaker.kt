package gg.flags.client

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.Duration

class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeout: Duration = Duration.ofMinutes(1)
) {
    private var failureCount = 0
    private var lastFailureTime: Instant? = null
    private var state: State = State.CLOSED
    private val mutex = Mutex()

    enum class State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    suspend fun recordSuccess() {
        mutex.withLock {
            failureCount = 0
            state = State.CLOSED
        }
    }

    suspend fun recordFailure() {
        mutex.withLock {
            failureCount++
            lastFailureTime = Instant.now()
            
            if (failureCount >= failureThreshold) {
                state = State.OPEN
            }
        }
    }

    suspend fun canExecute(): Boolean {
        mutex.withLock {
            return when (state) {
                State.CLOSED -> true
                State.OPEN -> {
                    val now = Instant.now()
                    lastFailureTime?.let { lastFailure ->
                        if (Duration.between(lastFailure, now) > resetTimeout) {
                            state = State.HALF_OPEN
                            true
                        } else {
                            false
                        }
                    } ?: true
                }
                State.HALF_OPEN -> true
            }
        }
    }

    suspend fun getState(): State {
        mutex.withLock {
            // Check if we should transition from OPEN to HALF_OPEN
            if (state == State.OPEN) {
                val now = Instant.now()
                lastFailureTime?.let { lastFailure ->
                    if (Duration.between(lastFailure, now) > resetTimeout) {
                        state = State.HALF_OPEN
                    }
                }
            }
            return state
        }
    }
}