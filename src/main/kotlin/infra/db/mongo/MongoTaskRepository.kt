package infra.db.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import model.Task
import model.TaskId
import model.TaskRepository
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

data class TaskDO(
    @BsonId val id: ObjectId?, val taskId: String, val expression: NodeDO.ExpressionDO
) {
    fun toModel(): Task {
        return Task(
            taskId, expression.toModel()
        )
    }
}

fun Task.toMongo(oid: ObjectId?): TaskDO {
    return TaskDO(
        oid, id, expression.toMongo()
    )
}

object MongoTaskRepository : TaskRepository {

    private const val TASKS_TABLE = "tasks"
    override suspend fun save(task: Task) {
        MongoConnection.getCollection<TaskDO>(TASKS_TABLE).replaceOne(
            Filters.eq(TaskDO::taskId.name, task.id), task.toMongo(null), ReplaceOptions().upsert(true)
        )
    }

    override suspend fun get(id: TaskId): Task? {
        return MongoConnection.getCollection<TaskDO>(TASKS_TABLE).find<TaskDO>(Filters.eq(TaskDO::taskId.name, id))
            .map { it.toModel() }.firstOrNull()
    }

    override suspend fun delete(id: TaskId) {
        MongoConnection.getCollection<TaskDO>(TASKS_TABLE).deleteOne(Filters.eq(TaskDO::taskId.name, id))
    }
}