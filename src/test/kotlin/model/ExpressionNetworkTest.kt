package model

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.concurrent.timerTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class MockNodeRepo : NodeRepository {
    val idMap: MutableMap<NodeId, Node> = ConcurrentHashMap()
    val funcMap: MutableMap<FuncId, ArrayList<Node>> = ConcurrentHashMap()
    val expressionMap: MutableMap<Expression, Node> = ConcurrentHashMap()
    val inputMap: MutableMap<SymbolId, ArrayList<Node>> = ConcurrentHashMap()
    val outputMap: MutableMap<SymbolId, Node> = ConcurrentHashMap()

    override suspend fun save(node: Node): Node {
        val nodeCopy = node.copy()
        idMap[nodeCopy.id] = nodeCopy
        funcMap.computeIfAbsent(nodeCopy.expression.funcId) { ArrayList() }
            .add(nodeCopy) // fix ConcurrentModificationException when updateFuncId
        nodeCopy.expression.inputs.forEach {
            inputMap.computeIfAbsent(it.ids[0]) { ArrayList() }.add(nodeCopy) // ConcurrentModificationException
        }
        nodeCopy.expression.outputs.forEach {
            outputMap[it] = nodeCopy
        }
        val expression = nodeCopy.expression
        val queryExpression = expression.copy()
        queryExpression.outputs = emptyList()
        expressionMap[queryExpression] = nodeCopy
        return nodeCopy
    }

    override suspend fun queryByExpression(expression: Expression): Node? {
        val queryExpression = expression.copy()
        queryExpression.outputs = emptyList() // output should not be considered
        return expressionMap[queryExpression]
    }

    override suspend fun queryByInput(id: SymbolId): List<Node> = inputMap[id] ?: emptyList()

    override suspend fun queryByOutput(id: SymbolId): Node? = outputMap[id]

    override suspend fun queryByFunc(funcId: FuncId): List<Node> = funcMap[funcId] ?: emptyList()
    override suspend fun queryAllRoot(): List<Node> {
        TODO("Not yet implemented")
    }

    override suspend fun queryAllNonRoot(): List<Node> {
        TODO("Not yet implemented")
    }

    override suspend fun saveAll(nodes: Iterable<Node>) {
        TODO("Not yet implemented")
    }

    override suspend fun get(id: NodeId): Node? {
        TODO("Not yet implemented")
    }

}

class MockTaskRepo : TaskRepository {
    val idMap: MutableMap<TaskId, Task> = ConcurrentHashMap()

    override suspend fun save(task: Task) {
        val taskCopy = task.copy()
        idMap[taskCopy.id] = taskCopy
    }

    override suspend fun get(id: TaskId): Task? = idMap[id]
    override suspend fun getTaskByDataId(id: SymbolId): Task? {
        return idMap.values.find { it.expression.outputs.contains(id) }
    }


    override suspend fun delete(id: TaskId) {
        idMap.remove(id)
    }

    override suspend fun getTaskByDataIdAndTo(id: SymbolId, to: Pointer): Task? {
        return idMap.values.filter { t -> t.expression.outputs.contains(id) && t.to == to }.firstOrNull()
    }

}

class MockExecutor : Executor {
    lateinit var callback: ExpressionNetwork
    var isSuccess: Boolean = true

    override suspend fun run(expression: Expression, from: Pointer, to: Pointer, withId: TaskId): Boolean {
        Timer().schedule(timerTask {
            runBlocking {
                if (isSuccess) {
                    callback.succeedRun(withId)
                } else {
                    callback.failedRun(withId, "Failed Reason")
                }
            }
        }, 100) // 主要是为了隔离线程
        return true
    }

    override suspend fun tryCancel(id: TaskId) {

    }

}

class MockMQ : MessageQueue {
    override suspend fun pushRunning(id: SymbolId) {
        return
    }

    override suspend fun pushRunFailed(id: SymbolId, reason: String) {
        return
    }

    override suspend fun pushRunFinish(id: SymbolId) {
        return
    }

    override suspend fun pushSystemFailed(id: SymbolId) {
        TODO("Not yet implemented")
    }

}

class MockPerf : PerformanceService {
    override suspend fun calculate(id: SymbolId) {
    }

}

class ExpressionNetworkTest(
    val nodeRepository: MockNodeRepo,
    val taskRepository: MockTaskRepo,
    val executor: MockExecutor
) : ExpressionNetwork(nodeRepository, taskRepository, executor, MockMQ(), MockPerf()) {

}

fun List<SymbolId>.toInputs(): List<Input> {
    return this.map { Input(type = InputType.DataId, ids = listOf(it)) }
}

object TestCases {
    val d1 = SymbolId("d1")
    val d2 = SymbolId("d2")
    val d3 = SymbolId("d3")
    val p1 = SymbolId("p1")
    val p2 = SymbolId("p2")
    val f1 = FuncId("f1")
    val f2 = FuncId("f2")

    private fun setUp(): ExpressionNetworkTest {
        val e = ExpressionNetworkTest(
            MockNodeRepo(),
            MockTaskRepo(),
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
                    inputs = listOf(
                        Input(type = InputType.DataId, ids = listOf(d1)),
                        Input(type = InputType.DataId, ids = listOf(d2))
                    ),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )

            for (id in genIds) {
                assertNotNull(en.nodeRepository.queryByOutput(id))
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            genIds = en.add(
                Expression(
                    inputs = genIds.toInputs(),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            for (id in genIds) {
                assertNotNull(en.nodeRepository.queryByOutput(id))
                assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            en.add(Expression.makeRoot(d3))
            assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(d3)!!.expectedPtr)
            genIds = en.add(
                Expression(
                    inputs = listOf(d1, d3).toInputs(),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
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
    fun testDuplicatedAdd() {
        val en = setUp()
        runBlocking {
            en.add(Expression.makeRoot(d1))
            en.add(Expression.makeRoot(d2))
            val expr = Expression(
                inputs = listOf(d1, d2).toInputs(),
                outputs = listOf(p1),
                f1,
                dataflow = "",
                arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
            )
            val ids1 = en.add(expr)
            val ids2 = en.add(expr)
            assertEquals(ids1, ids2)
        }
    }

    @Test
    fun testRunRoot() {
        val en = setUp()
        runBlocking {
            en.add(Expression.makeRoot(d1))
            en.add(Expression.makeRoot(d2))
            en.updateRoot(d1, Pointer(10))
            en.updateRoot(d2, Pointer(10))
            assertEquals(Pointer(10), en.nodeRepository.queryByOutput(d1)!!.effectivePtr)
            assertEquals(Pointer(10), en.nodeRepository.queryByOutput(d2)!!.effectivePtr)
            en.updateRoot(d1, Pointer(42))
            en.updateRoot(d2, Pointer(42))
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
                    inputs = listOf(d1, d2).toInputs(),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp2 = en.add(
                Expression(
                    inputs = exp1.toInputs(),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            en.executor.isSuccess = true // mock succeed exec
            en.updateRoot(d1, Pointer(10))
            // we don't need wait for exec because this case shouldn't cause any run in executor
            for (id in exp1) {
                // 计算应该没有传播下来，因为只有一个上游更新了
                assertEquals(Pointer(0), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
                assertEquals(Pointer(0), en.nodeRepository.queryByOutput(id)!!.effectivePtr)
            }
            en.updateRoot(d2, Pointer(10))
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
            val exp3Inputs = ArrayList<SymbolId>(exp1)
            exp3Inputs.add(d3)
            val exp3 = en.add(
                Expression(
                    inputs = exp3Inputs.toInputs(),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            for (id in exp3) {
                // exp3 应该没有更新 因为还有d3没有更新
                assertEquals(Pointer(0), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            // 更新d3
            en.updateRoot(d3, Pointer(10))
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

    @Test
    fun testUpdateFunc() {
        val en = setUp()
        runBlocking {
            en.add(Expression.makeRoot(d1))
            en.add(Expression.makeRoot(d2))
            val exp1 = en.add(
                Expression(
                    inputs = listOf(d1, d2).toInputs(),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp2 = en.add(
                Expression(
                    inputs = exp1.toInputs(),
                    outputs = listOf(p1),
                    f2,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp3 = en.add(
                Expression(
                    inputs = exp2.toInputs(),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )

            en.updateRoot(d1, Pointer(10))
            en.updateRoot(d2, Pointer(10))
            delay(150)
            assertEquals(Pointer(10), en.nodeRepository.queryByOutput(exp1[0])!!.effectivePtr)

            en.updateFunc(f1)
            // exp1运行完毕，有效指针为0
            assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(exp1[0])!!.effectivePtr)

            // exp2正在运行，有效指针为0
            assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(exp2[0])!!.effectivePtr)

            // exp3没有运行，有效指针为0
            assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(exp3[0])!!.effectivePtr)

            // 等所有传播完毕
            delay(300)
            assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(exp1[0])!!.effectivePtr)

            assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(exp2[0])!!.effectivePtr)

            assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(exp3[0])!!.effectivePtr)
        }
    }

    @Test
    fun testFailedRun() {
        val en = setUp()
        runBlocking {
            en.add(Expression.makeRoot(d1))
            en.add(Expression.makeRoot(d2))
            val exp1 = en.add(
                Expression(
                    inputs = listOf(d1, d2).toInputs(),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp2 = en.add(
                Expression(
                    inputs = exp1.toInputs(),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp3 = en.add(
                Expression(
                    inputs = exp2.toInputs(),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            en.executor.isSuccess = true
            en.updateRoot(d1, Pointer(10))
            en.updateRoot(d2, Pointer(10))
            for (id in exp1) {
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            delay(150)
            for (id in exp1) {
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.effectivePtr)
            }

            en.executor.isSuccess = false // 模拟exp1执行成功, exp2执行失败
            for (id in exp2) {
                assertEquals(Pointer(10), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            delay(150)
            for (id in exp2) {
                assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(id)!!.effectivePtr)
                assertEquals(false, en.nodeRepository.queryByOutput(id)!!.valid)
            }

            delay(150)
            for (id in exp3) {
                assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(id)!!.expectedPtr)
                assertEquals(Pointer.ZERO, en.nodeRepository.queryByOutput(id)!!.effectivePtr)
                assertEquals(false, en.nodeRepository.queryByOutput(id)!!.valid)
            }
        }
    }
}

