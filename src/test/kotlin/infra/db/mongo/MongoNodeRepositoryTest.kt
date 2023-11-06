package infra.db.mongo

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
                inputs = listOf(DataId("open_test"), DataId("close_test")),
                outputs = listOf(DataId("en_node_mongo_repo_test")),
                funcId = "add_test",
                shapeRule = Expression.ShapeRule(1, 1),
                alignmentRule = Expression.AlignmentRule(mapOf(DataId("open_test") to 1, DataId("close_test") to 1)),
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
        val ret = mongoNodeRepository.save(node)
        assertEquals(node, ret)
    }

    @Test
    fun queryByExpression() {
        val ret = mongoNodeRepository.queryByExpression(expression = node.expression)
        assertEquals(node, ret)
    }

    @Test
    fun queryByInput() {
        val ret = mongoNodeRepository.queryByInput(id = DataId("open_test"))
        assertEquals(1, ret.size)
        assertEquals(node, ret.first())
    }

    @Test
    fun queryByOutput() {
        val ret = mongoNodeRepository.queryByOutput(id = DataId("en_node_mongo_repo_test"))
        assertEquals(node, ret)
    }

    @Test
    fun queryByFunc() {
        val ret = mongoNodeRepository.queryByFunc(funcId = "add_test")
        assertEquals(1, ret.size)
        assertEquals(node, ret.first())
    }

    @Test
    fun queryIdByOutput() {
        val ret = mongoNodeRepository.queryIdByOutput(id = DataId("en_node_mongo_repo_test"))
        assertEquals(node.id, ret)
    }

}