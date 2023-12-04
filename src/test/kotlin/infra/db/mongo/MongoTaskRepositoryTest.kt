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
            )
        )
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
    fun delete() {
        runBlocking {

            mongoTaskRepository.delete(task.id)

            val ret = mongoTaskRepository.get(task.id)
            assertNull(ret)
        }
    }

}