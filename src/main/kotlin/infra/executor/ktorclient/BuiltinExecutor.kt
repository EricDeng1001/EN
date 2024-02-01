package infra.executor.ktorclient

import model.Executor
import model.Task
import model.TaskId

class BuiltinExecutor: Executor {
    override suspend fun run(task: Task): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun tryCancel(id: TaskId) {
        TODO("Not yet implemented")
    }
}