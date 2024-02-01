package model

interface Executor {
    suspend fun run(task: Task): Boolean


//    suspend fun runBatch(batch: BatchExpression, from: Pointer, to: Pointer, withId: TaskId)

    suspend fun tryCancel(id: TaskId)

    suspend fun deleteData(id: DataId)
}