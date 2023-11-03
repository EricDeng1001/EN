package model

interface Executor {
    fun run(expression: Expression, from: Pointer, to: Pointer, withId: TaskId)

    fun tryCancel(id: TaskId)
}