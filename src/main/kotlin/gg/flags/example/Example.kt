package gg.flags.example

import gg.flags.client.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create client using the new Go-style API
    val client = FlagsClient.NewClient(
        WithBaseURL("https://api.flags.gg"),
        WithAuth(Auth(
            projectId = "your-project-id",
            agentId = "your-agent-id", 
            environmentId = "your-environment-id"
        )),
        WithMemory()
    )
    
    try {
        // Example 1: Using the Go-style fluent API (synchronous)
        val result = client.Is("dark-mode").Enabled()
        if (result) {
            println("Dark mode is enabled")
        } else {
            println("Dark mode is disabled")
        }
        
        // Example 2: Using flag object with async check
        val betaFeature = client.flag("beta-feature")
        if (betaFeature.isEnabled()) {
            println("Beta feature is available")
        }
        
        // Example 3: Direct async check (original API)
        if (client.isEnabled("premium-features")) {
            println("Premium features are enabled")
        }
        
        // Example 4: Get all flags
        println("\nAll feature flags:")
        client.getAllFlags().forEach { flag ->
            println("- ${flag.details.name}: ${if (flag.enabled) "✓" else "✗"}")
        }
        
        // Example 5: Environment variable override
        // Set FLAGS_EXPERIMENTAL_FEATURE=true in your environment
        if (client.Is("experimental-feature").Enabled()) {
            println("\nExperimental feature is enabled (possibly via environment variable)")
        }
        
    } finally {
        // Always close the client to clean up resources
        client.close()
    }
}