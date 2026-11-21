package io.github.corebonts.muxgrid.core.htsp

sealed interface HtsValue {
    data class MapVal(val value: HtsMap) : HtsValue
    data class S64(val value: Long) : HtsValue
    data class Str(val value: String) : HtsValue
    data class Bin(val value: ByteArray) : HtsValue {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bin) return false
            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int = value.contentHashCode()
    }

    data class ListVal(val items: List<HtsValue>) : HtsValue
    data class Bool(val value: Boolean) : HtsValue
    data class Uuid(val bytes: ByteArray) : HtsValue {
        init {
            require(bytes.size == 16) { "UUID must be exactly 16 bytes, got ${bytes.size}" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Uuid) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

class HtsMap(
    private val fields: LinkedHashMap<String, HtsValue> = LinkedHashMap(),
) : Map<String, HtsValue> by fields {

    fun getLong(key: String): Long? = when (val v = fields[key]) {
        is HtsValue.S64 -> v.value
        is HtsValue.Bool -> if (v.value) 1L else 0L
        else -> null
    }

    fun getInt(key: String): Int? = getLong(key)?.toInt()

    fun getString(key: String): String? = (fields[key] as? HtsValue.Str)?.value

    fun getBytes(key: String): ByteArray? = (fields[key] as? HtsValue.Bin)?.value

    fun getList(key: String): List<HtsValue>? = (fields[key] as? HtsValue.ListVal)?.items

    fun getMap(key: String): HtsMap? = (fields[key] as? HtsValue.MapVal)?.value

    fun getBoolean(key: String): Boolean? = when (val v = fields[key]) {
        is HtsValue.Bool -> v.value
        is HtsValue.S64 -> v.value != 0L
        else -> null
    }

    fun getLongList(key: String): List<Long> =
        getList(key).orEmpty().mapNotNull { (it as? HtsValue.S64)?.value }

    fun getMapList(key: String): List<HtsMap> =
        getList(key).orEmpty().mapNotNull { (it as? HtsValue.MapVal)?.value }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HtsMap) return false
        return fields == other.fields
    }

    override fun hashCode(): Int = fields.hashCode()

    override fun toString(): String = "HtsMap($fields)"

    class Builder {
        private val map = LinkedHashMap<String, HtsValue>()

        fun put(key: String, value: Long) {
            map[key] = HtsValue.S64(value)
        }

        fun put(key: String, value: Int) {
            map[key] = HtsValue.S64(value.toLong())
        }

        fun put(key: String, value: String) {
            map[key] = HtsValue.Str(value)
        }

        fun put(key: String, value: ByteArray) {
            map[key] = HtsValue.Bin(value)
        }

        fun put(key: String, value: Boolean) {
            map[key] = HtsValue.Bool(value)
        }

        fun put(key: String, value: HtsMap) {
            map[key] = HtsValue.MapVal(value)
        }

        fun put(key: String, value: List<HtsValue>) {
            map[key] = HtsValue.ListVal(value)
        }

        fun putUuid(key: String, bytes: ByteArray) {
            map[key] = HtsValue.Uuid(bytes)
        }

        fun put(key: String, value: HtsValue) {
            map[key] = value
        }

        fun putAll(other: HtsMap) {
            map.putAll(other)
        }

        fun build(): HtsMap = HtsMap(LinkedHashMap(map))
    }
}

inline fun htsMap(block: HtsMap.Builder.() -> Unit): HtsMap =
    HtsMap.Builder().apply(block).build()
