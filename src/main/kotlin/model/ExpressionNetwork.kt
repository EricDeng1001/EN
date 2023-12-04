package model

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap


abstract class ExpressionNetwork(
    private val nodeRepository: NodeRepository,
    private val taskRepository: TaskRepository,
    private val executor: Executor,
    private val messageQueue: MessageQueue,
    private val performanceService: PerformanceService
) {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)
    private val loadedNodes: MutableMap<Node.Id, Node> = ConcurrentHashMap()
    private val locks: MutableMap<Node.Id, Mutex> = ConcurrentHashMap()

    private suspend fun getNode(id: DataId): Node? {
        val node = nodeRepository.queryByOutput(id) ?: return null
        return saveToLoaded(node)
    }

    private suspend fun findNodeByInput(id: DataId): List<Node> {
        val nodes = nodeRepository.queryByInput(id)
        return nodes.map { saveToLoaded(it) }
    }

    private suspend fun saveToLoaded(node: Node): Node {
        var it = node
        val mutex = locks.computeIfAbsent(it.id) { Mutex() }
        mutex.withLock {
            val loadedNode = loadedNodes[it.id]
            if (loadedNode != null) {
                it = loadedNode
            } else {
                loadedNodes[it.id] = it
            }
            return it
        }
    }

    suspend fun buildGraph(ids: List<DataId>): Graph {
        val expressions: MutableList<Expression> = ArrayList()
        for (id in ids) {
            val node = getNode(id) ?: continue
            expressions.add(node.expression)
        }
        return Graph(expressions)
    }

    suspend fun queryExpressionsState(ids: List<DataId>): List<Pair<DataId, Boolean?>> {
        val res: MutableList<Pair<DataId, Boolean?>> = mutableListOf()
        for (id in ids) {
            val node = getNode(id)
            if (node == null) {
                res.add(Pair(id, null))
            } else {
                res.add(Pair(id, node.effectivePtr > Pointer.ZERO))
            }
        }
        return res
    }

    suspend fun runRoot(id: DataId, effectivePtr: Pointer) {
        val node = getNode(id) ?: return
        if (node.expression.isRoot()) {
            runRootNode(node, effectivePtr)
        }
    }

    suspend fun markForceRunPerf(id: DataId) {
        val node = getNode(id) ?: return
        node.isPerfCalculated = false
        nodeRepository.save(node)
    }

    suspend fun runExpression(id: DataId) {
        val node = getNode(id) ?: return
        if (node.expression.isRoot().not()) {
            tryRunExpressionNode(node)
        }
    }

    private suspend fun runRootNode(node: Node, effectivePtr: Pointer) {
        node.effectivePtr = effectivePtr
        node.expectedPtr = node.effectivePtr
        nodeRepository.save(node)
        updateDownstream(node)
    }

    private suspend fun tryRunExpressionNode(node: Node) {
        // end 3
        if (!node.valid) {
            endRun(node)
            for (id in node.ids()) {
                messageQueue.pushRunFailed(id)
            }
            return
        }
        val mutex =
            locks[node.id]!! // must exists, or the code is wrong, for tryRun should ways happens after get/load node

        mutex.withLock {
            if (node.isRunning) { // somehow double run, doesn't matter, we can safe ignore this
                return
            }

            if (!node.shouldRun()) {
                if (node.isPerfCalculated.not()) {
                    try {
                        performanceService.calculate(node.expression.outputs[0])
                        node.isPerfCalculated = true
                        nodeRepository.save(node)
                    } catch (e: Exception) {
                        logger.error("calculate performance error: $e")
                    }
                }
                endRun(node)
                return
            }

            val task = Task(
                id = genId(), expression = node.expression
            )

            try {
                taskRepository.save(task)
                logger.info("try to tun expression node: $task")
                node.lastStartTime = Clock.System.now()
                executor.run(node.expression, withId = task.id, from = node.effectivePtr, to = node.expectedPtr)
                node.isRunning = true
                for (id in node.ids()) {
                    messageQueue.pushRunning(id)
                }
            } catch (e: Exception) {
                logger.error("try to run expression node err: $e")
                taskRepository.delete(task.id)
                for (id in node.ids()) {
                    messageQueue.pushSystemFailed(id)
                }
                endRun(node)
            }
        }
    }

    private suspend fun updateDownstream(root: Node) {
        for (node in loadDownstream(root.expression)) {
            node.expectedPtr = findExpectedPtr(node.expression)
            nodeRepository.save(node)  // update expected ptr
            tryRunExpressionNode(node) // try run (this start a new run session)
        }
    }

    private suspend fun findExpectedPtr(expression: Expression): Pointer {
        val nodes = loadUpstream(expression)
        var expectedPtr: Pointer = Pointer.MAX
        for (node in nodes) {
            expectedPtr = minOf(node.effectivePtr, expectedPtr)
        }
        return expectedPtr
    }

    // end 1
    suspend fun succeedRun(id: TaskId) {
        logger.info("succeed run: $id")
        val task = taskRepository.get(id) ?: return
        val node = getNode(task.expression.outputs[0])!!
        val mutex = locks[node.id]!!
        node.lastUpdateTime = Clock.System.now()
        var reset: Boolean
        mutex.withLock {
            node.isRunning = false
            reset = node.resetPtr
            if (!reset) { // a success run
                node.effectivePtr = node.expectedPtr
                if (!node.isPerfCalculated) {
                    try {
                        for (output in node.expression.outputs) {
                            performanceService.calculate(output)
                        }
                        node.isPerfCalculated = true
                    } catch (e: Exception) {
                        logger.error("calculate performance error: $e")
                    }
                }
                nodeRepository.save(node)
            }
        }

        if (!reset) {
            updateDownstream(node)
        }

        endRun(node)

        taskRepository.delete(id)
        for (output in node.ids()) {
            messageQueue.pushRunFinish(output)
        }
    }

    private fun endRun(node: Node) {
        locks.remove(node.id) // always release lock's mem when finish running
        loadedNodes.remove(node.id)
    }

    suspend fun failedRun(id: TaskId) {
        logger.info("failed run: $id")
        val task = taskRepository.get(id) ?: return
        taskRepository.delete(id)
        val node = loadedNodes[Node.Id(task.expression.outputs[0])]!!
        for (id in node.ids()) {
            messageQueue.pushRunFailed(id)
        }
        endRun(node)
        markInvalid(node)
    }

    // end 2
    private suspend fun markInvalid(node: Node) {
        node.valid = false
        node.effectivePtr = Pointer.ZERO
        nodeRepository.save(node)
        for (dNode in loadDownstream(node.expression)) {
            markInvalid(dNode)
        }
    }

//    fun timeoutRun(id: TaskId) {
//        executor.tryCancel(id)
//        val task = taskRepository.get(id)
//        val node = nodeRepository.queryByOutput(task.expression.outputs[0])
//        executor.run()
//    }

    suspend fun updateFunc(funcId: FuncId) {
        for (node in nodeRepository.queryByFunc(funcId)) {
            markNeedUpdate(node)
        }
    }

    private suspend fun markNeedUpdate(node: Node) {
        val mutex = locks.computeIfAbsent(node.id) { Mutex() }
        mutex.withLock {
            if (node.isRunning) {
                node.resetPtr = true
            }
            node.effectivePtr = Pointer.ZERO
            node.valid = true
            nodeRepository.save(node)
        }

        for (dNode in loadDownstream(node.expression)) {
            markNeedUpdate(dNode)
        }
    }


    suspend fun loadUpstream(expression: Expression): Set<Node> {
        val result = HashSet<Node>()
        for (input in expression.inputs) {
            for (id in input.ids) {
                result.add(getNode(id) ?: continue)
            }
        }
        return result
    }


    suspend fun loadDownstream(expression: Expression): Set<Node> {
        val result = HashSet<Node>()
        for (input in expression.outputs) {
            result.addAll(findNodeByInput(input))
        }
        return result
    }

    suspend fun add(expression: Expression): List<DataId> {
        if (expression.isRoot()) {
            return saveRoot(expression)
        }

        return saveExpression(expression)
    }

    private suspend fun saveExpression(expression: Expression): List<DataId> {
        val queryByExpression = nodeRepository.queryByExpression(expression)
        if (queryByExpression != null) return queryByExpression.expression.outputs

        for (input in expression.inputs) {
            for (id in input.ids) {
                if (getNode(id) == null) { // invalid expression ref unknown data id
                    throw IllegalArgumentException("ref $input is unknown")
                }
            }
        }

        val result = ArrayList<DataId>()
        for (output in expression.outputs) {
            result.add(DataId(genId()))
        }
        expression.outputs = result
        val node = Node(
            effectivePtr = Pointer.ZERO,
            expectedPtr = findExpectedPtr(expression),
            expression = expression,
            valid = true,
            resetPtr = false,
            isRunning = false
        )
        nodeRepository.save(node)
        return result
    }

    private suspend fun saveRoot(expression: Expression): List<DataId> {
        if (nodeRepository.queryByOutput(expression.outputs[0]) == null) {
            val node = Node(
                effectivePtr = Pointer.ZERO,
                expectedPtr = Pointer.ZERO,
                expression = expression,
                valid = true,
                resetPtr = false,
                isRunning = false
            )
            nodeRepository.save(node)
        }
        return listOf(expression.outputs[0])
    }

    private fun genId() = "__" + UUID.randomUUID().toString().replace("-", "")


}