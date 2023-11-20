package model

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.collections.ArrayList
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

    override suspend fun save(node: Node): Node {
        val nodeCopy = node.copy()
        nodeCopy.resetPtr = false // db does not mem these two
        nodeCopy.isRunning = false
        idMap[nodeCopy.id] = nodeCopy
        funcMap.computeIfAbsent(nodeCopy.expression.funcId) { CopyOnWriteArraySet() }
            .add(nodeCopy) // fix ConcurrentModificationException when updateFuncId
        nodeCopy.expression.inputs.forEach {
            inputMap.computeIfAbsent(it) { CopyOnWriteArraySet() }.add(nodeCopy) // ConcurrentModificationException
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

    override suspend fun queryByInput(id: DataId): Set<Node> = inputMap[id] ?: emptySet()

    override suspend fun queryByOutput(id: DataId): Node? = outputMap[id]

    override suspend fun queryByFunc(funcId: FuncId): Set<Node> = funcMap[funcId] ?: emptySet()

}

class MockTaskRepo : TaskRepository {
    val idMap: MutableMap<TaskId, Task> = ConcurrentHashMap()

    override suspend fun save(task: Task) {
        val taskCopy = task.copy()
        idMap[taskCopy.id] = taskCopy
    }

    override suspend fun get(id: TaskId): Task? = idMap[id]
    override suspend fun delete(id: TaskId) {
        idMap.remove(id)
    }

}

class MockExecutor : Executor {
    lateinit var callback: ExpressionNetwork
    var isSuccess: Boolean = true

    override suspend fun run(expression: Expression, from: Pointer, to: Pointer, withId: TaskId) {
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

    override suspend fun tryCancel(id: TaskId) {

    }

}

class MockMessageQueue: MessageQueue{
    override suspend fun pushRunning(id: DataId) {
    }

    override suspend fun pushRunFailed(id: DataId) {
    }

    override suspend fun pushRunFinish(id: DataId) {
    }

}

class ExpressionNetworkTest(
    val nodeRepository: MockNodeRepo,
    val taskRepository: MockTaskRepo,
    val executor: MockExecutor,
    val messageQueue: MessageQueue
) : ExpressionNetwork(nodeRepository, taskRepository, executor, messageQueue) {

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
            MockExecutor(),
            MockMessageQueue()
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
                    inputs = genIds,
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
                    inputs = listOf(d1, d3),
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
                inputs = listOf(d1, d2),
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
            en.runRoot(d1, Pointer(10))
            en.runRoot(d2, Pointer(10))
            assertEquals(Pointer(10), en.nodeRepository.queryByOutput(d1)!!.effectivePtr)
            assertEquals(Pointer(10), en.nodeRepository.queryByOutput(d2)!!.effectivePtr)
            en.runRoot(d1, Pointer(42))
            en.runRoot(d2, Pointer(42))
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
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp2 = en.add(
                Expression(
                    inputs = exp1,
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            en.executor.isSuccess = true // mock succeed exec
            en.runRoot(d1, Pointer(10))
            // we don't need wait for exec because this case shouldn't cause any run in executor
            for (id in exp1) {
                // 计算应该没有传播下来，因为只有一个上游更新了
                assertEquals(Pointer(0), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
                assertEquals(Pointer(0), en.nodeRepository.queryByOutput(id)!!.effectivePtr)
            }
            en.runRoot(d2, Pointer(10))
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
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            for (id in exp3) {
                // exp3 应该没有更新 因为还有d3没有更新
                assertEquals(Pointer(0), en.nodeRepository.queryByOutput(id)!!.expectedPtr)
            }
            // 更新d3
            en.runRoot(d3, Pointer(10))
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
                    inputs = listOf(d1, d2),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp2 = en.add(
                Expression(
                    inputs = exp1,
                    outputs = listOf(p1),
                    f2,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp3 = en.add(
                Expression(
                    inputs = exp2,
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )

            en.runRoot(d1, Pointer(10))
            en.runRoot(d2, Pointer(10))
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
                    inputs = listOf(d1, d2),
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp2 = en.add(
                Expression(
                    inputs = exp1,
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            val exp3 = en.add(
                Expression(
                    inputs = exp2,
                    outputs = listOf(p1),
                    f1,
                    dataflow = "",
                    arguments = mapOf(Pair("arg1", Argument(type = "float", value = "10")))
                )
            )
            en.executor.isSuccess = true
            en.runRoot(d1, Pointer(10))
            en.runRoot(d2, Pointer(10))
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

