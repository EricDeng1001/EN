package model

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.concurrent.timerTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


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
        val queryExpression = expression.copy()
        queryExpression.outputs = emptyList() // output should not be considered
        return expressionMap[queryExpression]
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
        }, 100) // 主要是为了隔离线程
    }

    override fun tryCancel(id: TaskId) {

    }

}

class ExpressionNetworkTest(
    val nodeRepository: MockNodeRepo,
    val taskRepository: MockTaskRepo, val dataManager: MockDataManager, val executor: MockExecutor
) : ExpressionNetwork(nodeRepository, taskRepository, dataManager, executor) {

}

object TestCases {
    val d1 = DataId("d1")
    val d2 = DataId("d2")
    val d3 = DataId("d3")
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
        e.executor.callback = e
        return e
    }

    @Test
    fun testAddExpression() {
        val en = setUp()
        runBlocking {
            en.add(Expression.makeRoot(d1))
            en.add(Expression.makeRoot(d2))
            assertNotNull(en.nodeRepository.queryByOutput(d1))
            assertNotNull(en.nodeRepository.queryByOutput(d2))
            assertEquals(Pointer(0), en.nodeRepository.queryByOutput(d1)!!.effectivePtr)
            assertEquals(Pointer(0), en.nodeRepository.queryByOutput(d2)!!.effectivePtr)
            // manually set effective ptr to see post add
            en.nodeRepository.queryByOutput(d1)!!.effectivePtr = Pointer(10)
            en.nodeRepository.queryByOutput(d2)!!.effectivePtr = Pointer(10)
            var genIds = en.add(
                Expression(
                    inputs = listOf(d1, d2),
                    outputs = listOf(p1),
                    f1,
                    shapeRule = Expression.ShapeRule(1, 1),
                    alignmentRule = Expression.AlignmentRule(mapOf(Pair(d1, 0), Pair(d2, 0))),
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )

            for (id in genIds) {
                assertNotNull(en.nodeRepository.queryByOutput(id))
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            genIds = en.add(
                Expression(
                    inputs = genIds,
                    outputs = listOf(p1),
                    f1,
                    shapeRule = Expression.ShapeRule(1, 1),
                    alignmentRule = Expression.AlignmentRule(mapOf(Pair(d1, 0), Pair(d2, 0))),
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            for (id in genIds) {
                assertNotNull(en.nodeRepository.queryByOutput(id))
                assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            en.add(Expression.makeRoot(d3))
            genIds = en.add(
                Expression(
                    inputs = listOf(d1, d3),
                    outputs = listOf(p1),
                    f1,
                    shapeRule = Expression.ShapeRule(1, 1),
                    alignmentRule = Expression.AlignmentRule(mapOf(Pair(d1, 0), Pair(d2, 0))),
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            for (id in genIds) {
                assertNotNull(en.nodeRepository.queryByOutput(id))
                // because d3 is zero, even if d1 is 10, this should be zero
                assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
        }
    }

    @Test
    fun testRunRoot() {
        val en = setUp()
        runBlocking {
            en.add(Expression.makeRoot(d1))
            en.add(Expression.makeRoot(d2))
            en.dataManager.ptr = Pointer(10) // mock data update
            en.run(d1)
            en.run(d2)
            assertEquals(Pointer(10), en.nodeRepository.queryByOutput(d1)!!.effectivePtr)
            assertEquals(Pointer(10), en.nodeRepository.queryByOutput(d2)!!.effectivePtr)
            en.dataManager.ptr = Pointer(42) // mock data update
            en.run(d1)
            en.run(d2)
            assertEquals(Pointer(42), en.nodeRepository.queryByOutput(d1)!!.effectivePtr)
            assertEquals(Pointer(42), en.nodeRepository.queryByOutput(d2)!!.effectivePtr)
        }
    }


    @Test
    fun testRunSpread() {
        val en = setUp()
        runBlocking {
            en.add(Expression.makeRoot(d1))
            en.add(Expression.makeRoot(d2))
            val exp1 = en.add(
                Expression(
                    inputs = listOf(d1, d2),
                    outputs = listOf(p1),
                    f1,
                    shapeRule = Expression.ShapeRule(1, 1),
                    alignmentRule = Expression.AlignmentRule(mapOf(Pair(d1, 0), Pair(d2, 0))),
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp2 = en.add(
                Expression(
                    inputs = exp1,
                    outputs = listOf(p1),
                    f1,
                    shapeRule = Expression.ShapeRule(1, 1),
                    alignmentRule = Expression.AlignmentRule(mapOf(Pair(d1, 0), Pair(d2, 0))),
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            en.dataManager.ptr = Pointer(10) // mock data update
            en.executor.isSuccess = true // mock succeed exec
            en.run(d1)
            // we don't need wait for exec because this case shouldn't cause any run in executor
            for (id in exp1) {
                // 计算应该没有传播下来，因为只有一个上游更新了
                assertEquals(Pointer(0), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
                assertEquals(Pointer(0), en.nodeRepository.queryByOutput(id)!!.effectivePtr)
            }
            en.run(d2)
            // 计算应该已经传播下来，其中expected ptr是立刻传播, effective ptr会在计算完成后传播
            for (id in exp1) {
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            delay(150) // wait for "succeed run" for this node is now exec
            for (id in exp1) {
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.effectivePtr)
            }
            // exp1 算完后， exp2 expected ptr应该立刻得到传播
            for (id in exp2) {
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            delay(150) // wait for "succeed run" for this node is now exec
            for (id in exp2) {
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.effectivePtr)
            }
            en.add(Expression.makeRoot(d3))
            val exp3Inputs = ArrayList<DataId>(exp1)
            exp3Inputs.add(d3)
            val exp3 = en.add(
                Expression(
                    inputs = exp3Inputs,
                    outputs = listOf(p1),
                    f1,
                    shapeRule = Expression.ShapeRule(1, 1),
                    alignmentRule = Expression.AlignmentRule(mapOf(Pair(d1, 0), Pair(d2, 0))),
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            for (id in exp3) {
                // exp3 应该没有更新 因为还有d3没有更新
                assertEquals(Pointer(0), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            // 更新d3
            en.run(d3)
            for (id in exp3) {
                // exp3 应该更新expectedPtr
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            delay(150)
            for (id in exp3) {
                // exp3 应该更新effectivePtr
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.effectivePtr)
            }
        }
    }

}

