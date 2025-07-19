package gg.flags.cache

import gg.flags.model.Details
import gg.flags.model.FeatureFlag
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.*

class SQLiteCacheTest {
    private lateinit var cache: SQLiteCache
    private val testDbPath = "test_flags_cache.db"
    
    @BeforeTest
    fun setup() = runTest {
        // Clean up any existing test database
        File(testDbPath).delete()
        
        cache = SQLiteCache(testDbPath)
        cache.init()
    }
    
    @AfterTest
    fun tearDown() = runTest {
        cache.close()
        File(testDbPath).delete()
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
    fun `should persist flags across cache instances`() = runTest {
        val flags = listOf(
            FeatureFlag(true, Details("persistent-feature", "1"))
        )
        
        cache.refresh(flags, 60)
        cache.close()
        
        // Create new cache instance with same database
        val newCache = SQLiteCache(testDbPath)
        newCache.init()
        
        val (enabled, exists) = newCache.get("persistent-feature")
        assertTrue(enabled)
        assertTrue(exists)
        
        newCache.close()
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
        cache.refresh(emptyList(), 1) // 1 second interval
        
        assertFalse(cache.shouldRefreshCache())
        
        // Simulate time passing
        kotlinx.coroutines.delay(1100)
        
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
    
    @Test
    fun `should handle transaction rollback on error`() = runTest {
        val initialFlags = listOf(
            FeatureFlag(true, Details("feature1", "1"))
        )
        
        cache.refresh(initialFlags, 60)
        
        // Close the connection to simulate an error
        cache.close()
        
        try {
            cache.refresh(listOf(FeatureFlag(true, Details("feature2", "2"))), 60)
            fail("Should have thrown exception")
        } catch (e: Exception) {
            // Expected
        }
        
        // Reinitialize and check that original data is still there
        cache = SQLiteCache(testDbPath)
        cache.init()
        
        val allFlags = cache.getAll()
        assertEquals(1, allFlags.size)
        assertEquals("feature1", allFlags[0].details.name)
    }
}