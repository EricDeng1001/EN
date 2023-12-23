package model

import kotlinx.datetime.Instant
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
    var isPerfCalculated: Boolean = false
) {
    @JvmInline
    value class Id(val id: DataId)
    fun shouldRun(): Boolean = expectedPtr > effectivePtr

    fun isRunRoot(): Boolean = expression.isRoot() || mustCalculate

    val id get() = Id(expression.outputs[0])

    val idStr get() = id.id.str
    fun ids(): Iterable<DataId> {
        return expression.outputs
    }
}