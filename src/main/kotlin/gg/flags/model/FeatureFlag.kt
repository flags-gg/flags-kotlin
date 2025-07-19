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
    val details: Details
)