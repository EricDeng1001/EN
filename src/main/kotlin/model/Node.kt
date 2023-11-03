package model

import kotlinx.serialization.Serializable

@Serializable
open class Node(
    open var valid: Boolean = true,
    var effectivePtr: Pointer,
    var expectedPtr: Pointer,
    val expression: Expression,
    var isRunning: Boolean,
    var toResetPtr: Boolean
)