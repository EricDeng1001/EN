package model

import kotlinx.serialization.Serializable

typealias NodeId = Node.Id
@Serializable
data class Node(
    var valid: Boolean = true,
    var effectivePtr: Pointer,
    var expectedPtr: Pointer,
    var isRunning: Boolean,
    var resetPtr: Boolean,
    val expression: Expression
) {
    @JvmInline
    value class Id(val id: DataId)
    fun shouldRun(): Boolean = expectedPtr > effectivePtr

    val id: Id
        get() = Id(expression.outputs[0])
}