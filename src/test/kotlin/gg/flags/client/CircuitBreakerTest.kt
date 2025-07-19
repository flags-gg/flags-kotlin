package gg.flags.client

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.*

class CircuitBreakerTest {
    
    @Test
    fun `should start in closed state`() = runTest {
        val breaker = CircuitBreaker()
        
        assertTrue(breaker.canExecute())
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState())
    }
    
    @Test
    fun `should open after failure threshold`() = runTest {
        val breaker = CircuitBreaker(failureThreshold = 3)
        
        // Record failures
        breaker.recordFailure()
        assertTrue(breaker.canExecute())
        
        breaker.recordFailure()
        assertTrue(breaker.canExecute())
        
        breaker.recordFailure() // This should open the circuit
        assertFalse(breaker.canExecute())
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState())
    }
    
    @Test
    fun `should reset on success`() = runTest {
        val breaker = CircuitBreaker(failureThreshold = 2)
        
        breaker.recordFailure()
        breaker.recordSuccess()
        
        // Should reset failure count
        breaker.recordFailure()
        assertTrue(breaker.canExecute()) // Still closed because count was reset
    }
    
    @Test
    fun `should transition to half-open after timeout`() = runBlocking {
        val breaker = CircuitBreaker(
            failureThreshold = 1,
            resetTimeout = Duration.ofMillis(100)
        )
        
        breaker.recordFailure()
        assertFalse(breaker.canExecute())
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState())
        
        // Wait for timeout
        kotlinx.coroutines.delay(200)
        
        assertTrue(breaker.canExecute()) // Should be half-open now
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState())
    }
    
    @Test
    fun `should close circuit on success in half-open state`() = runBlocking {
        val breaker = CircuitBreaker(
            failureThreshold = 1,
            resetTimeout = Duration.ofMillis(100)
        )
        
        breaker.recordFailure()
        kotlinx.coroutines.delay(150)
        
        assertTrue(breaker.canExecute()) // Half-open
        breaker.recordSuccess()
        
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState())
    }
}