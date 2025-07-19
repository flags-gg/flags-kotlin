package gg.flags.client

import gg.flags.cache.Cache
import gg.flags.cache.MemoryCache
import gg.flags.cache.SQLiteCache
import io.ktor.client.*
import java.time.Duration

interface ClientOption {
    fun apply(builder: FlagsClient.Builder)
}

fun WithBaseURL(url: String) = object : ClientOption {
    override fun apply(builder: FlagsClient.Builder) {
        builder.baseUrl(url)
    }
}

fun WithAuth(auth: Auth) = object : ClientOption {
    override fun apply(builder: FlagsClient.Builder) {
        builder.auth(auth)
    }
}

fun WithMemory() = object : ClientOption {
    override fun apply(builder: FlagsClient.Builder) {
        builder.withMemoryCache()
    }
}

fun WithSQLite(dbPath: String = "flags_cache.db") = object : ClientOption {
    override fun apply(builder: FlagsClient.Builder) {
        builder.withSQLiteCache(dbPath)
    }
}

fun WithCache(cache: Cache) = object : ClientOption {
    override fun apply(builder: FlagsClient.Builder) {
        builder.cache(cache)
    }
}

fun WithHttpClient(client: HttpClient) = object : ClientOption {
    override fun apply(builder: FlagsClient.Builder) {
        builder.httpClient(client)
    }
}

fun WithMaxRetries(retries: Int) = object : ClientOption {
    override fun apply(builder: FlagsClient.Builder) {
        builder.maxRetries(retries)
    }
}

fun WithFailureThreshold(threshold: Int) = object : ClientOption {
    override fun apply(builder: FlagsClient.Builder) {
        builder.failureThreshold(threshold)
    }
}

fun WithResetTimeout(timeout: Duration) = object : ClientOption {
    override fun apply(builder: FlagsClient.Builder) {
        builder.resetTimeout(timeout)
    }
}