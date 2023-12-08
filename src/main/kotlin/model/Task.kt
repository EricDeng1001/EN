package model

import kotlinx.datetime.Instant

typealias TaskId = String

data class Task(
    val id: TaskId,
    val expression: Expression,
    val from: Pointer,
    val to: Pointer,
    var start: Instant = Instant.DISTANT_PAST,
    var finish: Instant = Instant.DISTANT_PAST,
    var failedReason: String? = null
) {
}