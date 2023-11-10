package model

interface Executor {
    suspend fun run(expression: Expression, from: Pointer, to: Pointer, withId: TaskId)

    suspend fun tryCancel(id: TaskId)
}