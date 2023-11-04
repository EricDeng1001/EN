package model

interface TaskRepository {
    fun save(task: Task)

    fun get(id: TaskId): Task?

    fun delete(id: TaskId)
}