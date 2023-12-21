package model

interface TaskRepository {
    suspend fun save(task: Task)

    suspend fun get(id: TaskId): Task?

    suspend fun getList(ids: List<TaskId>): List<Task>

    suspend fun delete(id: TaskId)
}