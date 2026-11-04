package gg.flags.client

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
}
