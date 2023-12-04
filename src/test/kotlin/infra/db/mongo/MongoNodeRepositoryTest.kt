package infra.db.mongo

import kotlinx.coroutines.runBlocking
import model.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MongoNodeRepositoryTest {

    private val mongoNodeRepository = MongoNodeRepository

    private lateinit var node: Node

    @BeforeEach
    fun setUp() {
        node = Node(
            valid = true,
            effectivePtr = Pointer(0),
            expectedPtr = Pointer(4840),
            isRunning = false,
            resetPtr = false,
            expression = Expression(
                inputs = listOf(
                    Input(type = InputType.DataId, ids = listOf(DataId("open_test"))),
                    Input(type = InputType.DataId, ids = listOf(DataId("close_test")))),
                outputs = listOf(DataId("en_node_mongo_repo_test")),
                funcId = FuncId("add_test"),
                dataflow = "",
                arguments = mapOf("const" to Argument("1", "int"))
            ),
        )
        save()
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun save() {
        runBlocking {
            val ret = mongoNodeRepository.save(node)
            assertEquals(node, ret)
        }
    }

    @Test
    fun queryByExpression() {
        runBlocking {
            val ret = mongoNodeRepository.queryByExpression(expression = node.expression)
            assertEquals(node, ret)
        }
    }

    @Test
    fun queryByInput() {
        runBlocking {
            val ret = mongoNodeRepository.queryByInput(id = DataId("open_test"))
            assertEquals(1, ret.size)
            assertEquals(node, ret.first())
        }
    }

    @Test
    fun queryByOutput() {
        runBlocking {
            val ret = mongoNodeRepository.queryByOutput(id = DataId("en_node_mongo_repo_test"))
            assertEquals(node, ret)
        }
    }

    @Test
    fun queryByFunc() {
        runBlocking {
            val ret = mongoNodeRepository.queryByFunc(funcId = FuncId("add_test"))
            assertEquals(1, ret.size)
            assertEquals(node, ret.first())
        }
    }

}