package model

import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashSet
import kotlin.concurrent.timerTask
import kotlin.test.Test
import kotlin.test.assertEquals


class MockNodeRepo : NodeRepository {
    val idMap: MutableMap<NodeId, Node> = ConcurrentHashMap()
    val funcMap: MutableMap<FuncId, MutableSet<Node>> = ConcurrentHashMap()
    val expressionMap: MutableMap<Expression, Node> = ConcurrentHashMap()
    val inputMap: MutableMap<DataId, MutableSet<Node>> = ConcurrentHashMap()
    val outputMap: MutableMap<DataId, Node> = ConcurrentHashMap()

    override fun save(node: Node): Node {
        idMap[node.id] = node
        funcMap.computeIfAbsent(node.expression.funcId) { HashSet() }.add(node)
        expressionMap[node.expression] = node
        node.expression.inputs.forEach {
            inputMap.computeIfAbsent(it) { HashSet() }.add(node)
        }
        node.expression.outputs.forEach {
            outputMap[it] = node
        }
        return node
    }

    override fun queryByExpression(expression: Expression): Node? {
        expression.outputs = emptyList() // output should not be considered
        return expressionMap[expression]
    }

    override fun queryByInput(id: DataId): Set<Node> = inputMap[id] ?: emptySet()

    override fun queryByOutput(id: DataId): Node? = outputMap[id]

    override fun queryByFunc(funcId: FuncId): Set<Node> = funcMap[funcId] ?: emptySet()

}

class MockTaskRepo : TaskRepository {
    val idMap: MutableMap<TaskId, Task> = ConcurrentHashMap()

    override fun save(task: Task) {
        idMap[task.id] = task
    }

    override fun get(id: TaskId): Task? = idMap[id]
    override fun delete(id: TaskId) {
        idMap.remove(id)
    }

}

class MockDataManager : DataManager {
    var ptr: Pointer = Pointer.ZERO

    override fun findLastPtr(id: DataId): Pointer {
        return ptr
    }

}

class MockExecutor : Executor {
    lateinit var callback: ExpressionNetwork
    var isSuccess: Boolean = true

    override fun run(expression: Expression, from: Pointer, to: Pointer, withId: TaskId) {
        Timer().schedule(timerTask {
            runBlocking {
                if (isSuccess) {
                    callback.succeedRun(withId)
                } else {
                    callback.failedRun(withId)
                }
            }
        }, 2000)
    }

    override fun tryCancel(id: TaskId) {

    }

}

class ExpressionNetworkTest(
    val nodeRepository: NodeRepository,
    val taskRepository: TaskRepository, val dataManager: DataManager, val executor: Executor
) : ExpressionNetwork(nodeRepository, taskRepository, dataManager, executor) {

}

object TestCases {
    val d1 = DataId("d1")
    val d2 = DataId("d2")
    val p1 = DataId("p1")
    val p2 = DataId("p2")
    val f1 = FuncId("f1")
    val f2 = FuncId("f2")

    private fun setUp(): ExpressionNetworkTest {
        val e = ExpressionNetworkTest(
            MockNodeRepo(),
            MockTaskRepo(),
            MockDataManager(),
            MockExecutor()
        )
        runBlocking {
            e.add(Expression.makeRoot(d1))
            e.add(Expression.makeRoot(d2))
        }
        return e
    }

    @Test
    fun testAddExpression() {
        val en = setUp()
        runBlocking {
            val genIds = en.add(
                Expression(
                    inputs = listOf(d1, d2),
                    outputs = listOf(p1),
                    f1,
                    shapeRule = Expression.ShapeRule(1, 1),
                    alignmentRule = Expression.AlignmentRule(mapOf(Pair(d1, 0), Pair(d2, 0))),
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
        }

    }
}

