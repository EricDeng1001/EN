package model

interface TaskRepository {
    suspend fun save(task: Task)

    suspend fun get(id: TaskId): Task?

    suspend fun getListByDataIds(ids: List<String>): List<Task>

    suspend fun delete(id: TaskId)
}