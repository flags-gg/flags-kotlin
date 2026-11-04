package gg.flags.client

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/**
 * Percentage rollouts. Must match the API and every other SDK exactly:
 * bucket = FNV-1a 32-bit of UTF-8 "<flag name>:<targeting key>", modulo 100.
 */
object Rollout {
    fun bucket(flagName: String, targetingKey: String): Int {
        var h = 0x811c9dc5u
        for (b in "$flagName:$targetingKey".encodeToByteArray()) {
            h = (h xor b.toUByte().toUInt()) * 0x01000193u
        }
        return (h % 100u).toInt()
    }

    /** Without a key only a full rollout applies, so partial rollouts fail closed. */
    fun inRollout(flagName: String, targetingKey: String?, rollout: Int?): Boolean {
        val pct = rollout ?: 100
        return when {
            pct >= 100 -> true
            pct <= 0 || targetingKey.isNullOrEmpty() -> false
            else -> bucket(flagName, targetingKey) < pct
        }
    }

    /**
     * The user as the server's targeting rules see it: base64 of UTF-8 JSON, with the targeting key as "key".
     * Attribute values are strings or lists of strings.
     */
    fun encodeContext(targetingKey: String?, context: Map<String, Any>): String {
        val fields = context.mapValues { (_, v) ->
            when (v) {
                is Iterable<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> JsonPrimitive(v.toString())
            }
        }.toMutableMap()
        if (!targetingKey.isNullOrEmpty()) fields["key"] = JsonPrimitive(targetingKey)
        return Base64.getEncoder().encodeToString(JsonObject(fields).toString().toByteArray(Charsets.UTF_8))
    }
}
