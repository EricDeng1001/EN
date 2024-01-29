package model

import kotlinx.serialization.Serializable

@Serializable
enum class NodeState(val value: String) {
    RUNNING("running"),
    FAILED("failed"),
    FINISHED("finished"),
    SYSTEM_FAILED("system-failed");

}

typealias NodeId = Node.Id

@Serializable
data class Node(
    var valid: Boolean = true,
    var effectivePtr: Pointer,
    var expectedPtr: Pointer,
    val expression: Expression,
    var mustCalculate: Boolean = false,
    var isPerfCalculated: Boolean = false,
    var shouldUpdate: Boolean = false,
    var depth: Int = 0
) {

    @Serializable
    @JvmInline
    value class Id(val str: String)

    fun shouldRun(): Boolean = expectedPtr > effectivePtr

    val id get() = Id(expression.outputs[0].str)

    val idStr get() = id.str
    fun ids(): Iterable<SymbolId> {
        return expression.outputs
    }

    companion object {
        fun makeRoot(expression: Expression) = Node(
            expression = expression,
            effectivePtr = Pointer.ZERO,
            expectedPtr = Pointer.ZERO,
            depth = 0,
            shouldUpdate = true
        )
    }
}