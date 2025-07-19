package gg.flags.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureFlagTest {
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun `should serialize and deserialize FeatureFlag correctly`() {
        val flag = FeatureFlag(
            enabled = true,
            details = Details(
                name = "test-feature",
                id = "123"
            )
        )
        
        val jsonString = json.encodeToString(FeatureFlag.serializer(), flag)
        val deserialized = json.decodeFromString(FeatureFlag.serializer(), jsonString)
        
        assertEquals(flag, deserialized)
    }
    
    @Test
    fun `should deserialize API response correctly`() {
        val jsonResponse = """
            {
                "intervalAllowed": 60,
                "flags": [
                    {
                        "enabled": true,
                        "details": {
                            "name": "feature1",
                            "id": "1"
                        }
                    },
                    {
                        "enabled": false,
                        "details": {
                            "name": "feature2",
                            "id": "2"
                        }
                    }
                ]
            }
        """.trimIndent()
        
        val response = json.decodeFromString(ApiResponse.serializer(), jsonResponse)
        
        assertEquals(60, response.intervalAllowed)
        assertEquals(2, response.flags.size)
        assertEquals("feature1", response.flags[0].details.name)
        assertEquals(true, response.flags[0].enabled)
        assertEquals("feature2", response.flags[1].details.name)
        assertEquals(false, response.flags[1].enabled)
    }
}