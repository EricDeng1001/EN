package model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

typealias NodeId = Node.Id
@Serializable
data class Node(
    var valid: Boolean = true,
    var effectivePtr: Pointer,
    var expectedPtr: Pointer,
    var isRunning: Boolean,
    var resetPtr: Boolean,
    val expression: Expression,
    var lastUpdateTime: Instant = Instant.DISTANT_PAST
) {
    @JvmInline
    value class Id(val id: DataId)
    fun shouldRun(): Boolean = expectedPtr > effectivePtr

    val id: Id
        get() = Id(expression.outputs[0])

    fun ids(): Iterable<DataId> {
        return expression.outputs
    }
}