package model

typealias TaskId = String
data class Task(val id: TaskId, val expression: Expression) {
}