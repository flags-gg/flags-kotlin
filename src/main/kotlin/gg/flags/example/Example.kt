package gg.flags.example

import gg.flags.client.FlagsClient
import gg.flags.client.Auth
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create client with your credentials
    val client = FlagsClient.builder()
        .auth(Auth(
            projectId = "your-project-id",
            agentId = "your-agent-id", 
            environmentId = "your-environment-id"
        ))
        .withMemoryCache() // Use memory cache for this example
        .build()
    
    try {
        // Example 1: Simple flag check
        if (client.isEnabled("dark-mode")) {
            println("Dark mode is enabled")
        } else {
            println("Dark mode is disabled")
        }
        
        // Example 2: Using flag object
        val betaFeature = client.flag("beta-feature")
        if (betaFeature.isEnabled()) {
            println("Beta feature is available")
        }
        
        // Example 3: Get all flags
        println("\nAll feature flags:")
        client.getAllFlags().forEach { flag ->
            println("- ${flag.details.name}: ${if (flag.enabled) "✓" else "✗"}")
        }
        
        // Example 4: Environment variable override
        // Set FLAGS_EXPERIMENTAL_FEATURE=true in your environment
        if (client.isEnabled("experimental-feature")) {
            println("\nExperimental feature is enabled (possibly via environment variable)")
        }
        
    } finally {
        // Always close the client to clean up resources
        client.close()
    }
}