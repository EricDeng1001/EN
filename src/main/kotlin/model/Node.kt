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
    var runRoot: Id
) {
    @Serializable
    @JvmInline
    value class Id(val str: String)
    fun shouldRun(): Boolean = expectedPtr > effectivePtr

    fun isRunRoot(): Boolean = expression.isRoot() || mustCalculate

    val id get() = Id(expression.outputs[0].str)

    val idStr get() = id.str
    fun ids(): Iterable<DataId> {
        return expression.outputs
    }

    companion object {
        fun makeRoot(expression: Expression) = Node(
            expression = expression,
            effectivePtr = Pointer.ZERO,
            expectedPtr = Pointer.ZERO,
            runRoot = Id(expression.outputs.first().str)
        )
    }
}