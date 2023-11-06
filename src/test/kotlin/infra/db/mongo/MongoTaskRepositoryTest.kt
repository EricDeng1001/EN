package infra.db.mongo

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
                inputs = listOf(DataId("open_test"), DataId("close_test")),
                outputs = listOf(DataId("en_node_mongo_repo_test")),
                funcId = FuncId("add_test"),
                shapeRule = Expression.ShapeRule(1, 1),
                alignmentRule = Expression.AlignmentRule(mapOf(DataId("open_test") to 1, DataId("close_test") to 1)),
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
        mongoTaskRepository.save(task)
    }

    @Test
    fun get() {
        val ret = mongoTaskRepository.get(task.id)
        assertEquals(task, ret)
    }

    @Test
    fun delete() {
        mongoTaskRepository.delete(task.id)

        val ret = mongoTaskRepository.get(task.id)
        assertNull(ret)
    }

}