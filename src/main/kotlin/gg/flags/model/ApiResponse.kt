package gg.flags.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    val intervalAllowed: Int,
    val flags: List<FeatureFlag>
)