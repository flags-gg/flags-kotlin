package gg.flags.model

import kotlinx.serialization.Serializable

@Serializable
data class Details(
    val name: String,
    val id: String
)

@Serializable
data class FeatureFlag(
    val enabled: Boolean,
    val details: Details,
    /** Percentage of users that see the flag when enabled; null means 100. */
    val rollout: Int? = null
)