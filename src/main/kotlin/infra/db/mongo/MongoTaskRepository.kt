package infra.db.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.*
import model.*
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.LocalDateTime

data class TaskDO(
    @BsonId val id: ObjectId?,
    val taskId: String,
    val expression: NodeDO.ExpressionDO,
    val from: Int? = null,
    val to: Int? = null,
    val start: LocalDateTime? = null,
    val finish: LocalDateTime? = null,
    var failedReason: String? = null
) {
    fun toModel(): Task {
        return Task(
            id = taskId,
            expression = expression.toModel(),
            from = from?.let { Pointer(it) } ?: Pointer.ZERO,
            to = to?.let { Pointer(it) } ?: Pointer.ZERO,
            start = start?.toKotlinLocalDateTime()?.toInstant(TimeZone.currentSystemDefault())
                ?: Instant.DISTANT_PAST,
            finish = finish?.toKotlinLocalDateTime()?.toInstant(TimeZone.currentSystemDefault())
                ?: Instant.DISTANT_PAST,
            failedReason = failedReason
        )
    }
}

fun Task.toMongo(oid: ObjectId?): TaskDO {
    return TaskDO(
        oid,
        taskId = id,
        expression = expression.toMongo(),
        from = from.value,
        to = to.value,
        start = start.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime(),
        finish = finish.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime(),
        failedReason = failedReason
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

    override suspend fun getLatestByDataId(id: DataId): Task? {
        return MongoConnection.getCollection<TaskDO>(TASKS_TABLE).find<TaskDO>(
            Filters.`in`("${TaskDO::expression.name}.${NodeDO.ExpressionDO::outputs.name}", id.str))
            .sort(Filters.eq(TaskDO::start.name, -1)).map { it.toModel() }.firstOrNull()
    }


}