package gg.flags.client

import gg.flags.cache.Cache
import gg.flags.cache.MemoryCache
import gg.flags.cache.SQLiteCache
import gg.flags.model.ApiResponse
import gg.flags.model.FeatureFlag
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class FlagsClient private constructor(
    private val baseUrl: String,
    private val auth: Auth,
    private val cache: Cache,
    private val httpClient: HttpClient,
    private val circuitBreaker: CircuitBreaker,
    private val maxRetries: Int,
    @Volatile private var targetingKey: String?,
    @Volatile private var context: Map<String, Any>
) : AutoCloseable {
    
    private val refreshJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + refreshJob)
    private val initialRefreshComplete = CompletableDeferred<Unit>()
    
    init {
        // Do initial refresh
        scope.launch {
            try {
                refreshFlags()
                initialRefreshComplete.complete(Unit)
            } catch (e: Exception) {
                initialRefreshComplete.completeExceptionally(e)
            }
        }
        
        // Start background refresh
        scope.launch {
            while (isActive) {
                delay(5.seconds)
                if (cache.shouldRefreshCache()) {
                    refreshFlags()
                }
            }
        }
    }
    
    suspend fun isEnabled(flagName: String): Boolean {
        // Check environment variable override first
        val envVarName = "FLAGS_${flagName.uppercase().replace("-", "_")}"
        // Check system property first (for testing), then environment variable
        val envValue = System.getProperty(envVarName) ?: System.getenv(envVarName)
        envValue?.let { value ->
            return value.lowercase() == "true"
        }
        
        // Check cache
        val (enabled, exists) = cache.get(flagName)
        if (exists) {
            return enabled
        }
        
        // If not in cache, wait for refresh and check again
        refreshFlags()
        val (enabledAfterRefresh, existsAfterRefresh) = cache.get(flagName)
        return if (existsAfterRefresh) enabledAfterRefresh else false
    }
    
    /**
     * Change who this client evaluates rollouts for (e.g. after login) and re-fetch,
     * so cached values match the new user.
     */
    suspend fun setTargetingKey(key: String?) {
        targetingKey = key
        refreshFlags()
    }

    /** Replace the attributes targeting rules see (e.g. email, plan, groups) and re-fetch. */
    suspend fun setContext(attributes: Map<String, Any>) {
        context = attributes
        refreshFlags()
    }

    fun Is(flagName: String): Flag {
        return Flag(flagName, this)
    }
    
    suspend fun flag(name: String): Flag {
        return Flag(name, this)
    }
    
    suspend fun getAllFlags(): List<FeatureFlag> {
        return cache.getAll()
    }
    
    internal suspend fun awaitInitialRefresh() {
        initialRefreshComplete.await()
    }
    
    private suspend fun refreshFlags() {
        if (!circuitBreaker.canExecute()) {
            logger.warn { "Circuit breaker is open, skipping API call" }
            return
        }
        
        var retries = 0
        var lastException: Exception? = null
        
        while (retries < maxRetries) {
            try {
                val response = httpClient.get("$baseUrl/flags") {
                    headers {
                        append("X-Project-ID", auth.projectId)
                        append("X-Environment-ID", auth.environmentId)
                        append("X-Agent-ID", auth.agentId)
                        append("X-Flags-Capabilities", "rollout")
                        append("X-Flags-Context", Rollout.encodeContext(targetingKey, context))
                    }
                }
                
                if (response.status.isSuccess()) {
                    val apiResponse = response.body<ApiResponse>()
                    // resolve rollouts once, so the cache (and isEnabled) just see enabled
                    val key = targetingKey
                    val resolved = apiResponse.flags.map {
                        it.copy(enabled = it.enabled && Rollout.inRollout(it.details.name, key, it.rollout))
                    }
                    cache.refresh(resolved, apiResponse.intervalAllowed)
                    circuitBreaker.recordSuccess()
                    return
                } else {
                    throw Exception("API returned status ${response.status}")
                }
            } catch (e: Exception) {
                lastException = e
                logger.error(e) { "Failed to refresh flags, attempt ${retries + 1}/$maxRetries" }
                retries++
                
                if (retries < maxRetries) {
                    delay((retries * 1000).toLong())
                }
            }
        }
        
        circuitBreaker.recordFailure()
        logger.error(lastException) { "Failed to refresh flags after $maxRetries attempts" }
    }
    
    override fun close() {
        refreshJob.cancel()
        httpClient.close()
        runBlocking {
            cache.close()
        }
    }
    
    class Builder {
        private var baseUrl: String = "https://api.flags.gg"
        private var auth: Auth? = null
        private var cache: Cache? = null
        private var httpClient: HttpClient? = null
        private var maxRetries: Int = 3
        private var failureThreshold: Int = 5
        private var resetTimeout: Duration = Duration.ofMinutes(1)
        private var targetingKey: String? = null
        private var context: Map<String, Any> = emptyMap()
        
        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun auth(auth: Auth) = apply { this.auth = auth }
        fun cache(cache: Cache) = apply { this.cache = cache }
        fun httpClient(client: HttpClient) = apply { this.httpClient = client }
        fun maxRetries(retries: Int) = apply { this.maxRetries = retries }
        fun failureThreshold(threshold: Int) = apply { this.failureThreshold = threshold }
        fun resetTimeout(timeout: Duration) = apply { this.resetTimeout = timeout }
        /** Stable id of the user or device, used for percentage rollouts. */
        fun targetingKey(key: String?) = apply { this.targetingKey = key }
        /** Attributes for targeting rules, e.g. mapOf("email" to email, "groups" to listOf("beta")). Evaluated by the server. */
        fun context(attributes: Map<String, Any>) = apply { this.context = attributes }
        
        fun withMemoryCache() = apply { this.cache = MemoryCache() }
        fun withSQLiteCache(dbPath: String = "flags_cache.db") = apply { 
            this.cache = SQLiteCache(dbPath) 
        }
        
        suspend fun build(): FlagsClient {
            val finalAuth = auth ?: throw IllegalArgumentException("Auth configuration is required")
            
            val finalCache = cache ?: MemoryCache()
            finalCache.init()
            
            val finalHttpClient = httpClient ?: HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
                
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.INFO
                }
                
                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 5_000
                }
                
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 0) // We handle retries manually
                }
            }
            
            val circuitBreaker = CircuitBreaker(failureThreshold, resetTimeout)
            
            return FlagsClient(
                baseUrl = baseUrl,
                auth = finalAuth,
                cache = finalCache,
                httpClient = finalHttpClient,
                circuitBreaker = circuitBreaker,
                maxRetries = maxRetries,
                targetingKey = targetingKey,
                context = context
            )
        }
    }
    
    companion object {
        fun builder() = Builder()
        
        suspend fun NewClient(vararg options: ClientOption): FlagsClient {
            val builder = Builder()
            options.forEach { it.apply(builder) }
            return builder.build()
        }
    }
}

data class Flag(
    val name: String,
    private val client: FlagsClient
) {
    suspend fun isEnabled(): Boolean = client.isEnabled(name)
    
    fun Enabled(): Boolean = runBlocking {
        client.isEnabled(name)
    }
}