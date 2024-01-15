package model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

typealias TaskId = String

@Serializable
data class Task(
    val id: TaskId,
    val expression: Expression,
    val from: Pointer,
    val to: Pointer,
    var start: Instant = Instant.DISTANT_PAST,
    var finish: Instant = Instant.DISTANT_PAST,
    var failedReason: String? = null,
    val priority: Int = 0
) {
    val nodeId get() = expression.outputs[0]
}