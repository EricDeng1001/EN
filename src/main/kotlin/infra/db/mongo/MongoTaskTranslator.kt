package infra.db.mongo

import model.Task
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

data class TaskDO(
    @BsonId
    val id: ObjectId?,
    val taskId: String,
    val expression: NodeDO.ExpressionDO
)

object MongoTaskTranslator {

    fun toMongo(task: Task, id: ObjectId?): TaskDO {
        return TaskDO(
            id,
            task.id,
            MongoNodeTranslator.toMongo(task.expression)
        )
    }

    fun toModel(task: TaskDO): Task {
        return Task(
            task.taskId,
            MongoNodeTranslator.toModel(task.expression)
        )
    }
}