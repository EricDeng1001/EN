package model

interface TaskRepository {
    suspend fun save(task: Task)

    suspend fun get(id: TaskId): Task?

    suspend fun getTaskByDataId(id: SymbolId): Task?

    suspend fun delete(id: TaskId)

    suspend fun getTaskByDataIdAndTo(id: SymbolId, to: Pointer): Task?
}