package infra.db.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import model.Task
import model.TaskId
import model.TaskRepository

object MongoTaskRepository : TaskRepository {

    private val collection = MongoConnection.defaultDatabase.getCollection<TaskDO>("tasks")
    private val translator = MongoTaskTranslator
    override fun save(task: Task) {
        runBlocking {
            collection.replaceOne(
                Filters.eq(TaskDO::taskId.name, task.id), translator.toMongo(task, null), ReplaceOptions().upsert(true)
            )
        }
        return
    }

    override fun get(id: TaskId): Task? {
        return runBlocking {
            collection.find<TaskDO>(Filters.eq(TaskDO::taskId.name, id)).map { translator.toModel(it) }.firstOrNull()
        }
    }

    override fun delete(id: TaskId) {
        return runBlocking {
            collection.deleteOne(Filters.eq(TaskDO::taskId.name, id))
        }
    }
}