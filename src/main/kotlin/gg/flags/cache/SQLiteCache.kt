package gg.flags.cache

import gg.flags.model.FeatureFlag
import gg.flags.model.Details
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

private val logger = KotlinLogging.logger {}

class SQLiteCache(private val dbPath: String = "flags_cache.db") : Cache {
    private var connection: Connection? = null
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val SQLITE_DRIVER_CLASS = "org.sqlite.JDBC"

        private fun checkDriverAvailable() {
            try {
                Class.forName(SQLITE_DRIVER_CLASS)
            } catch (e: ClassNotFoundException) {
                throw IllegalStateException(
                    "SQLite JDBC driver not found. To use SQLiteCache, add the sqlite-jdbc dependency: " +
                    "implementation(\"org.xerial:sqlite-jdbc:<version>\"). " +
                    "For Android, use MemoryCache instead or provide a custom Cache implementation.",
                    e
                )
            }
        }
    }

    override suspend fun init() = withContext(Dispatchers.IO) {
        try {
            checkDriverAvailable()
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            createTables()
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize SQLite cache" }
            throw e
        }
    }

    private fun createTables() {
        connection?.createStatement()?.use { statement ->
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS flags (
                    name TEXT PRIMARY KEY,
                    enabled INTEGER,
                    details TEXT
                )
            """)
            
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS refresh_info (
                    id INTEGER PRIMARY KEY,
                    last_refresh INTEGER,
                    interval_allowed INTEGER
                )
            """)
            
            // Initialize refresh info if not exists
            statement.executeUpdate("""
                INSERT OR IGNORE INTO refresh_info (id, last_refresh, interval_allowed) 
                VALUES (1, 0, 60)
            """)
        }
    }

    override suspend fun get(name: String): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val query = "SELECT enabled FROM flags WHERE name = ?"
            connection?.prepareStatement(query)?.use { statement ->
                statement.setString(1, name)
                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    val enabled = resultSet.getInt("enabled") == 1
                    Pair(enabled, true)
                } else {
                    Pair(false, false)
                }
            } ?: Pair(false, false)
        }
    }

    override suspend fun getAll(): List<FeatureFlag> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val flags = mutableListOf<FeatureFlag>()
            val query = "SELECT name, enabled, details FROM flags"
            
            connection?.createStatement()?.use { statement ->
                val resultSet = statement.executeQuery(query)
                while (resultSet.next()) {
                    val enabled = resultSet.getInt("enabled") == 1
                    val detailsJson = resultSet.getString("details")
                    val details = json.decodeFromString<Details>(detailsJson)
                    
                    flags.add(FeatureFlag(enabled, details))
                }
            }
            
            flags
        }
    }

    override suspend fun refresh(flags: List<FeatureFlag>, intervalAllowed: Int) {
        withContext(Dispatchers.IO) {
        mutex.withLock {
            val conn = connection ?: throw IllegalStateException("Database connection is closed")
            
                // Start transaction
                conn.autoCommit = false
                
                try {
                    // Clear existing flags
                    conn.createStatement().executeUpdate("DELETE FROM flags")
                    
                    // Insert new flags
                    val insertQuery = "INSERT INTO flags (name, enabled, details) VALUES (?, ?, ?)"
                    conn.prepareStatement(insertQuery).use { statement ->
                        flags.forEach { flag ->
                            statement.setString(1, flag.details.name)
                            statement.setInt(2, if (flag.enabled) 1 else 0)
                            statement.setString(3, json.encodeToString(flag.details))
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                    
                    // Update refresh info
                    val updateRefreshQuery = "UPDATE refresh_info SET last_refresh = ?, interval_allowed = ? WHERE id = 1"
                    conn.prepareStatement(updateRefreshQuery).use { statement ->
                        statement.setLong(1, Instant.now().epochSecond)
                        statement.setInt(2, intervalAllowed)
                        statement.executeUpdate()
                    }
                    
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
        }
        }
    }

    override suspend fun shouldRefreshCache(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val query = "SELECT last_refresh, interval_allowed FROM refresh_info WHERE id = 1"
            connection?.createStatement()?.use { statement ->
                val resultSet = statement.executeQuery(query)
                if (resultSet.next()) {
                    val lastRefresh = resultSet.getLong("last_refresh")
                    val intervalAllowed = resultSet.getInt("interval_allowed")
                    // If never refreshed (lastRefresh = 0), should refresh
                    if (lastRefresh == 0L) {
                        return@use true
                    } else {
                        val now = Instant.now().epochSecond
                        val elapsed = now - lastRefresh
                        return@use elapsed >= intervalAllowed
                    }
                } else {
                    true
                }
            } ?: true
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            connection?.close()
            connection = null
        }
    }
}