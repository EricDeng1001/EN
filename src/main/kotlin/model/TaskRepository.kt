package model

interface TaskRepository {
    suspend fun save(task: Task)

    suspend fun get(id: TaskId): Task?

    suspend fun getTaskByDataId(id: DataId): Task?

    suspend fun delete(id: TaskId)
}