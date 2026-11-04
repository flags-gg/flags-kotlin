# Flags.gg Kotlin Client

A Kotlin client library for the flags.gg feature flag management system. This library provides a simple interface for checking feature flags with built-in caching, circuit breaking, and environment variable overrides.

## Features

- 🚀 Simple, intuitive API
- 💾 Multiple caching strategies (Memory, SQLite)
- 🔄 Automatic cache refresh based on server intervals
- 🛡️ Circuit breaker pattern for fault tolerance
- 🔧 Environment variable overrides for local development
- 🧵 Thread-safe operations using Kotlin coroutines
- ⚡ Async/non-blocking HTTP requests using Ktor

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("gg.flags:flags-kotlin:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'gg.flags:flags-kotlin:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>gg.flags</groupId>
    <artifactId>flags-kotlin</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

### Traditional API

```kotlin
import gg.flags.client.FlagsClient
import gg.flags.client.Auth

suspend fun main() {
    // Create client with authentication
    val client = FlagsClient.builder()
        .auth(Auth(
            projectId = "your-project-id",
            agentId = "your-agent-id",
            environmentId = "your-environment-id"
        ))
        .build()
    
    // Check if a feature is enabled
    if (client.isEnabled("new-feature")) {
        println("New feature is enabled!")
    }
    
    // Using flag object
    val flag = client.flag("another-feature")
    if (flag.isEnabled()) {
        println("Another feature is enabled!")
    }
    
    // Get all flags
    val allFlags = client.getAllFlags()
    allFlags.forEach { flag ->
        println("${flag.details.name}: ${flag.enabled}")
    }
    
    // Don't forget to close the client
    client.close()
}
```

### Go-style API

```kotlin
import gg.flags.client.*

suspend fun main() {
    // Create client using Go-style API
    val client = FlagsClient.NewClient(
        WithAuth(Auth(
            projectId = "your-project-id",
            agentId = "your-agent-id",
            environmentId = "your-environment-id"
        )),
        WithMemory(),
        WithMaxRetries(3)
    )
    
    // Check if a feature is enabled using Is()
    if (client.Is("new-feature").Enabled()) {
        println("New feature is enabled!")
    }
    
    // Check multiple features
    val darkMode = client.Is("dark-mode")
    val betaFeature = client.Is("beta-feature")
    
    if (darkMode.Enabled() && betaFeature.Enabled()) {
        println("Both dark mode and beta feature are enabled!")
    }
    
    // Don't forget to close the client
    client.close()
}
```

## Configuration Options

### Cache Strategies

#### Memory Cache (Default for explicit memory cache)
```kotlin
val client = FlagsClient.builder()
    .auth(auth)
    .withMemoryCache()
    .build()
```

#### SQLite Cache (Default when no cache specified)
```kotlin
val client = FlagsClient.builder()
    .auth(auth)
    .withSQLiteCache("custom_flags.db") // Optional: specify database file
    .build()
```

### Advanced Configuration

#### Builder Pattern

```kotlin
val client = FlagsClient.builder()
    .auth(auth)
    .baseUrl("https://custom-api.flags.gg") // Custom API endpoint
    .maxRetries(5) // Number of retries on failure
    .failureThreshold(10) // Circuit breaker failure threshold
    .resetTimeout(Duration.ofMinutes(5)) // Circuit breaker reset timeout
    .httpClient(customHttpClient) // Custom Ktor HTTP client
    .build()
```

#### Go-style Options

```kotlin
val client = FlagsClient.NewClient(
    WithAuth(auth),
    WithBaseURL("https://custom-api.flags.gg"),
    WithMaxRetries(5),
    WithMemory(), // or WithSQLite("custom.db")
    WithHttpClient(customHttpClient)
)
```

## Percentage Rollouts

A flag can be rolled out to a percentage of users per environment, e.g. 10% of phones. Each user or device always gets the same answer. Give the client a stable `targetingKey`. Without one, partially rolled-out flags stay off.

On Android, use your signed-in user's id or a per-install id:

```kotlin
val prefs = context.getSharedPreferences("flags", Context.MODE_PRIVATE)
val installId = prefs.getString("install_id", null)
    ?: UUID.randomUUID().toString().also { prefs.edit().putString("install_id", it).apply() }

val client = FlagsClient.builder()
    .auth(auth)
    .targetingKey(user?.id ?: installId)
    .build()

// later, e.g. after login
client.setTargetingKey(user.id)
```

`isEnabled` already accounts for the rollout. The bucket is FNV-1a 32 of `"<flag>:<key>" % 100`, the same as the API and every other SDK.

## Targeting Rules

Flags can be turned on or off for specific users, groups or segments with rules set in the dashboard. Pass the user's attributes as `context`. The server evaluates the rules, so rule values never reach the device:

```kotlin
val client = FlagsClient.builder()
    .auth(auth)
    .targetingKey(user.id)
    .context(mapOf("email" to user.email, "plan" to user.plan, "groups" to user.groups))
    .build()

client.setContext(mapOf("plan" to "pro")) // e.g. after an upgrade; re-fetches
```

## Environment Variable Overrides

For local development and testing, you can override feature flags using environment variables:

```bash
# Enable a feature locally
export FLAGS_MY_FEATURE=true

# Disable a feature locally
export FLAGS_ANOTHER_FEATURE=false
```

The environment variable name format is `FLAGS_` + uppercase feature name with hyphens replaced by underscores.

## Circuit Breaker

The client includes a circuit breaker to handle API failures gracefully:

- **Closed State**: Normal operation, requests pass through
- **Open State**: After failure threshold is reached, requests are blocked
- **Half-Open State**: After reset timeout, allows one request to test if service is back

## Thread Safety

All operations are thread-safe and use Kotlin coroutines for concurrent access. The client automatically handles:

- Concurrent cache access using mutexes
- Safe flag updates during refresh
- Thread-safe circuit breaker state management

## Testing

For testing, you can use the memory cache and mock the HTTP client:

```kotlin
@Test
fun testWithMockClient() = runTest {
    val mockEngine = MockEngine { request ->
        respond(
            content = """{"intervalAllowed": 60, "flags": []}""",
            status = HttpStatusCode.OK
        )
    }
    
    val mockClient = HttpClient(mockEngine) {
        install(ContentNegotiation) { json() }
    }
    
    val flagsClient = FlagsClient.builder()
        .auth(Auth("test", "test", "test"))
        .httpClient(mockClient)
        .withMemoryCache()
        .build()
    
    // Your test logic here
}
```

## License

This project is licensed under the MIT License.