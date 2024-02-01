package infra.db.mongo

import kotlinx.coroutines.runBlocking
import model.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class MongoTaskRepositoryTest {

    private val mongoTaskRepository = MongoTaskRepository

    private lateinit var task: Task
    private lateinit var tasks: MutableMap<DataId, Task>

    @BeforeEach
    fun setUp() {
        task = Task(
            id = "123456789", expression = Expression(
                inputs = listOf(Input(type = InputType.DataId, ids = listOf(DataId("open_test"))),
                    Input(type = InputType.DataId, ids = listOf(DataId("close_test")))),
                outputs = listOf(DataId("en_node_mongo_repo_test")),
                funcId = FuncId("add_test"),
                dataflow = "",
                arguments = mapOf("const" to Argument("1", "int"))
            ),
            from = Pointer(0),
            to = Pointer(4840)
        )
        for (i in 1..4){
            val key = DataId(i.toString())
            tasks[key] = Task(
                id = key.str, expression = Expression(
                    inputs = listOf(Input(type = InputType.DataId, ids = listOf(DataId("open_test"))),
                        Input(type = InputType.DataId, ids = listOf(DataId("close_test")))),
                    outputs = listOf(key),
                    funcId = FuncId("add_test"),
                    dataflow = "",
                    arguments = mapOf("const" to Argument("1", "int"))
                ),
                from = Pointer(0),
                to = Pointer(4840)
            )
        }
        save()
    }

    @AfterEach
    fun tearDown() {
        delete()
    }

    @Test
    fun save() {
        runBlocking {
            mongoTaskRepository.save(task)
            tasks.forEach { mongoTaskRepository.save(it.value)  }
        }
    }

    @Test
    fun get() {
        runBlocking {
            val ret = mongoTaskRepository.get(task.id)
            assertEquals(task, ret)

        }
    }

    @Test
    fun getList() {
        runBlocking {
            val ret = tasks.mapNotNull { mongoTaskRepository.getTaskByDataId(it.key) }.toList()
            ret.forEach {
                assertNotNull(tasks[it.expression.outputs[0]])
            }
        }
    }


    @Test
    fun delete() {
        runBlocking {

            mongoTaskRepository.delete(task.id)

            val ret = mongoTaskRepository.get(task.id)
            assertNull(ret)
        }

        runBlocking {
            tasks.forEach { mongoTaskRepository.delete(it.key.str)  }
            tasks.forEach { assertNull(mongoTaskRepository.get(it.key.str))  }
        }
    }

}