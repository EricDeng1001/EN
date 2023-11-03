package model

import kotlinx.serialization.Serializable

@Serializable
data class Pointer(val value: Int): Comparable<Pointer> {

    companion object {
        val ZERO = Pointer(0)
        val MAX = Pointer(Int.MAX_VALUE)
    }

    override fun compareTo(other: Pointer): Int {
        return value - other.value
    }
}