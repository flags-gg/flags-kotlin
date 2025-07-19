package gg.flags.cache

import gg.flags.model.Details
import gg.flags.model.FeatureFlag
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MemoryCacheTest {
    private lateinit var cache: MemoryCache
    
    @BeforeTest
    fun setup() {
        cache = MemoryCache()
    }
    
    @Test
    fun `should return false for non-existent flag`() = runTest {
        val (enabled, exists) = cache.get("non-existent")
        
        assertFalse(enabled)
        assertFalse(exists)
    }
    
    @Test
    fun `should store and retrieve flags correctly`() = runTest {
        val flags = listOf(
            FeatureFlag(true, Details("feature1", "1")),
            FeatureFlag(false, Details("feature2", "2"))
        )
        
        cache.refresh(flags, 60)
        
        val (enabled1, exists1) = cache.get("feature1")
        assertTrue(enabled1)
        assertTrue(exists1)
        
        val (enabled2, exists2) = cache.get("feature2")
        assertFalse(enabled2)
        assertTrue(exists2)
    }
    
    @Test
    fun `should return all flags`() = runTest {
        val flags = listOf(
            FeatureFlag(true, Details("feature1", "1")),
            FeatureFlag(false, Details("feature2", "2")),
            FeatureFlag(true, Details("feature3", "3"))
        )
        
        cache.refresh(flags, 60)
        
        val allFlags = cache.getAll()
        assertEquals(3, allFlags.size)
        assertTrue(allFlags.any { it.details.name == "feature1" })
        assertTrue(allFlags.any { it.details.name == "feature2" })
        assertTrue(allFlags.any { it.details.name == "feature3" })
    }
    
    @Test
    fun `should handle refresh interval correctly`() = runTest {
        // Set a longer interval for testing
        cache.refresh(emptyList(), 3600) // 1 hour interval
        
        // Should not need refresh immediately after
        assertFalse(cache.shouldRefreshCache())
        
        // Test with very short interval
        cache.refresh(emptyList(), 0) // 0 second interval
        
        // Should always need refresh with 0 interval
        assertTrue(cache.shouldRefreshCache())
    }
    
    @Test
    fun `should clear old flags on refresh`() = runTest {
        val initialFlags = listOf(
            FeatureFlag(true, Details("feature1", "1")),
            FeatureFlag(false, Details("feature2", "2"))
        )
        
        cache.refresh(initialFlags, 60)
        assertEquals(2, cache.getAll().size)
        
        val newFlags = listOf(
            FeatureFlag(true, Details("feature3", "3"))
        )
        
        cache.refresh(newFlags, 60)
        
        val allFlags = cache.getAll()
        assertEquals(1, allFlags.size)
        assertEquals("feature3", allFlags[0].details.name)
        
        // Old flags should not exist
        val (_, exists1) = cache.get("feature1")
        assertFalse(exists1)
        val (_, exists2) = cache.get("feature2")
        assertFalse(exists2)
    }
}