package model

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class FuncId(val value: String) {
    companion object {
        val Noop = FuncId("")
    }
}
