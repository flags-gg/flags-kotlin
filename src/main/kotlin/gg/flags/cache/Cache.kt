package gg.flags.cache

import gg.flags.model.FeatureFlag

interface Cache {
    suspend fun get(name: String): Pair<Boolean, Boolean>
    suspend fun getAll(): List<FeatureFlag>
    suspend fun refresh(flags: List<FeatureFlag>, intervalAllowed: Int)
    suspend fun shouldRefreshCache(): Boolean
    suspend fun init()
    suspend fun close()
}