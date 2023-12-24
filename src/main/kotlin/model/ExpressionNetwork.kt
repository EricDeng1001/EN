package model

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.ArrayList
import kotlin.concurrent.withLock

abstract class ExpressionNetwork(
    private val nodeRepository: NodeRepository,
    private val taskRepository: TaskRepository,
    private val executor: Executor,
    private val messageQueue: MessageQueue,
    private val performanceService: PerformanceService
) {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)
    private val runRootStates: MutableMap<Node.Id, RunRootState> = ConcurrentHashMap()
    private val enLock: ReadWriteLock = ReentrantReadWriteLock()
    private val states: MutableMap<NodeId, NodeState> = ConcurrentHashMap()

    private val MUTEX_SIZE: Int = 1024
    private val nodeLocks: Array<Mutex> = Array(MUTEX_SIZE) { Mutex() }

    private class RunRootState(
        val lock: Mutex = Mutex(), val reqCount: AtomicInteger = AtomicInteger(0)
    )

    private suspend fun getNode(id: DataId): Node? {
        return nodeRepository.queryByOutput(id)
    }

    private suspend fun getNode(id: NodeId): Node? {
        return nodeRepository.get(id)
    }

    private suspend fun findNodeByInput(id: DataId): List<Node> {
        return nodeRepository.queryByInput(id)
    }

    suspend fun markMustCalc(ids: List<DataId>) {
        for (id in ids) {
            val node = getNode(id) ?: continue
            node.mustCalculate = true
            nodeRepository.save(node)
        }
    }

    suspend fun buildGraph(ids: List<DataId>): GraphView {
        val nodes: MutableList<Node> = ArrayList()
        for (id in ids) {
            val node = nodeRepository.queryByOutput(id) ?: continue
            nodes.add(node)
        }
        return Graph(nodes).view()
    }

    suspend fun buildDebugGraph(ids: List<DataId>): GraphDebugView {
        val nodes: MutableList<Node> = ArrayList()
        val inputs = HashSet<DataId>()
        for (id in ids) {
            val node = nodeRepository.queryByOutput(id) ?: continue
            nodes.add(node)

            inputs.addAll(node.expression.inputs.flatMap { it.ids }.toList())
        }
        val allInputs = inputs - ids.toSet()

        for (id in allInputs) {
            val node = nodeRepository.queryByOutput(id) ?: continue
            nodes.add(node)
        }

        return Graph(nodes).debugView()
    }

    suspend fun queryExpressionsState(ids: List<DataId>): List<Pair<DataId, String?>> {
        val res: MutableList<Pair<DataId, String?>> = mutableListOf()
        for (id in ids) {
            val state = states[NodeId(id.str)]
            if (state == null) {
                val node = nodeRepository.queryByOutput(id)
                if (node == null) {
                    res.add(id to null)
                } else {
                    res.add(
                        id to if (node.effectivePtr > Pointer.ZERO) NodeState.FINISHED.value
                        else if (!node.valid) NodeState.FAILED.value else null
                    )
                }
            } else {
                if (state == NodeState.SYSTEM_FAILED) {
                    val node = nodeRepository.queryByOutput(id)
                    if (node == null) {
                        res.add(id to null)
                    } else {
                        res.add(
                            id to if (node.effectivePtr > Pointer.ZERO) NodeState.FINISHED.value else null
                        )
                    }
                } else {
                    res.add(id to state.value)
                }
            }
        }
        return res
    }

    suspend fun updateRoot(id: DataId, effectivePtr: Pointer) {
        val node = getNode(id) ?: return
        if (node.expression.isRoot()) {
            node.effectivePtr = effectivePtr
            node.expectedPtr = effectivePtr
            nodeRepository.save(node)
            runRootNodeSafe(node)
        }
    }

    private fun runRootNodeSafe(node: Node) {
        enLock.readLock().withLock {
            runBlocking {
                val runRootState = getRunRootState(node)
                runRootState.reqCount.getAndIncrement()
                val lock = runRootState.lock
                if (runRootState.reqCount.get() > 1) {
                    runRootState.reqCount.getAndDecrement()
                    return@runBlocking
                }
                lock.withLock {
                    runRootState.reqCount.getAndDecrement()
                    val downstream = updateDownstream(node)
                    for (down in downstream) {
                        if (node.shouldUpdate) {
                            launch {
                                tryRunExpressionNode(node) // try run (this start a new run session)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun reUpdateRoot(id: DataId, resetPtr: Pointer) {
        val node = getNode(id) ?: return
        if (node.expression.isRoot()) {
            val save = node.effectivePtr
            val all = downstreamAllIncludeSelf(node) {
                it.effectivePtr = resetPtr
            }
            nodeRepository.saveAll(all)
            node.effectivePtr = save
            runRootNodeSafe(node)
        }
    }


    private suspend inline fun downstreamAllIncludeSelf(node: Node, action: (Node) -> Unit): HashSet<Node> {
        val toVisit = ArrayList<Node>()
        val visited = HashMap<Node.Id, Node>()
        toVisit.add(node)
        while (toVisit.isNotEmpty()) {
            val n = toVisit.first()
            toVisit.remove(n)
            visited[n.id] = n
            for (d in downstreamOneLevel(n)) {
                if (!visited.contains(d.id)) {
                    toVisit.add(d)
                }
            }
            action(n)
        }
        return visited.values.toHashSet()
    }

    suspend fun markForceRunPerf(id: DataId) {
        val node = getNode(id) ?: return
        node.isPerfCalculated = false
        nodeRepository.save(node)
    }

    suspend fun runExpression(id: DataId) {
        val node = getNode(id) ?: return
        if (!node.shouldUpdate) {
            enLock.readLock().withLock {
                runBlocking {
                    tryRunExpressionNode(node)
                }
            }
        }
    }


    private suspend fun updateDownstream(root: Node): List<Node> {
        val nextLevel = downstreamOneLevel(root)
        logger.debug("{} downstream size: {}, {}", root.idStr, nextLevel.size, nextLevel.map {
            it.expression.outputs[0].str
        }.toList())
        val changed = ArrayList<Node>()
        for (node in nextLevel) {
            val newPtr = if (node.expression.inputs.size == 1) {
                root.effectivePtr
            } else {
                logger.debug("{} -> {} findExp", root.idStr, node.idStr)
                findExpectedPtr(node.expression)
            }
            logger.debug(
                "{} inspecting: {}, exp: {}, newPtr: {}", root.idStr, node.idStr, node.expectedPtr.value, newPtr.value
            )
            // this check prevents double run
            if (newPtr != root.effectivePtr) { // not this update
                logger.warn("a double parent concurrent update happens, and node info leaked")
                continue
            }
            if (node.expectedPtr != newPtr) {
                logger.debug("{} updated downstream {} exp to {}", root.idStr, node.idStr, newPtr)
                node.expectedPtr = newPtr
                changed.add(node)
            }
        }

        nodeRepository.saveAll(changed)
        return changed
    }

    private suspend fun tryRunExpressionNode(node: Node) {
        try {
            logger.debug("{} enter tryRunExpressionNode", node.idStr)

            val mutex = getNodeLock(node)
            mutex.withLock {
                if (states.containsKey(node.id) && states[node.id] == NodeState.RUNNING) {
                    logger.debug("{} already running", node.idStr)
                    return
                }

                if (!node.valid) {
                    logger.debug("{} invalid", node.idStr)
                    pushFailed(node, "expression not valid due to upstream invalid or self invalid")
                    return
                }

                if (!node.shouldRun()) {
                    logger.debug("{} should not run", node.idStr)
                    if (node.effectivePtr != Pointer.ZERO) {
                        calcPerf(node)
                    }
                    return
                }

                val task = Task(
                    id = genId(),
                    expression = node.expression,
                    start = Clock.System.now(),
                    from = node.effectivePtr,
                    to = node.expectedPtr
                )
                try {
                    logger.info("try to run expression node: $task")
                    val started =
                        executor.run(node.expression, withId = task.id, from = node.effectivePtr, to = node.expectedPtr)
                    if (started) {
                        states[node.id] = NodeState.RUNNING
                        pushRunning(node)
                        taskRepository.save(task)
                    } else {
                        task.failedReason = "insufficient data to run"
                        taskRepository.save(task)
                    }
                } catch (e: Exception) {
                    logger.error("try to run expression node err: $e")
                    systemFailed(task, node, e.message.toString())
                }
            }
        } catch (e: Exception) {
            logger.error("${node.idStr} tryRunExpressionNode error: $e")
        }
    }

    private suspend fun findExpectedPtr(expression: Expression): Pointer {
        val nodes = upstreamOneLevel(expression)
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
        val node = getNode(task.nodeId)!!
        val mutex = getNodeLock(node)
        mutex.withLock {
            states[node.id] = NodeState.FINISHED
        }
        node.effectivePtr = task.to
        task.finish = Clock.System.now()
        nodeRepository.save(node)
        calcPerf(node)
        taskRepository.save(task)
        pushFinished(node)

        val downstream = updateDownstream(node)
        runBlocking {
            for (down in downstream) {
                if (down.shouldUpdate == node.shouldUpdate) {
                    launch {
                        tryRunExpressionNode(down) // try run (this start a new run session)
                    }
                }
            }
        }
    }

    suspend fun failedRun(id: TaskId, reason: String) {
        logger.info("failed run: $id")
        val task = taskRepository.get(id) ?: return
        task.finish = Clock.System.now()
        task.failedReason = reason
        taskRepository.save(task)
        val node = getNode(task.nodeId)!!
        val mutex = getNodeLock(node)
        mutex.withLock {
            states[node.id] = NodeState.FAILED
        }
        pushFailed(node, reason)
        downstreamAllIncludeSelf(node) {
            markInvalid(it)
        }
    }

    suspend fun systemFailedRun(id: TaskId, reason: String) {
        logger.info("system failed run: $id")
        val task = taskRepository.get(id) ?: return
        val node = getNode(task.nodeId)!!
        systemFailed(task, node, reason)
    }

    private suspend fun calcPerf(node: Node) {
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
    }

    private suspend fun systemFailed(task: Task, node: Node, reason: String) {
        val mutex = getNodeLock(node)
        mutex.withLock {
            states[node.id] = NodeState.SYSTEM_FAILED
        }
        pushSystemFailed(node)
        task.finish = Clock.System.now()
        task.failedReason = reason
        taskRepository.save(task)
    }


    // end 2
    private suspend fun markInvalid(node: Node) {
//        node.valid = false
        node.effectivePtr = Pointer.ZERO
        nodeRepository.save(node)
    }


    suspend fun updateFunc(funcId: FuncId) {
        enLock.writeLock().withLock {
            runBlocking {
                for (node in nodeRepository.queryByFunc(funcId)) {
                    markNeedUpdate(node)
                }
            }
        }

    }

    private suspend fun markNeedUpdate(node: Node) {
        node.effectivePtr = Pointer.ZERO
        node.valid = true
        nodeRepository.save(node)
    }

    private suspend fun upstreamOneLevel(expression: Expression): List<Node> {
        val result = ArrayList<Node>()
        for (input in expression.inputs) {
            for (id in input.ids) {
                result.add(getNode(id) ?: continue)
            }
        }
        return result
    }

    private suspend fun upstreamOneLevel(node: Node): List<Node> {
        return upstreamOneLevel(node.expression)
    }

    private suspend fun downstreamOneLevel(expression: Expression): List<Node> {
        val result = ArrayList<Node>()
        for (input in expression.outputs) {
            result.addAll(findNodeByInput(input))
        }
        return result
    }

    private suspend fun downstreamOneLevel(node: Node): List<Node> {
        return downstreamOneLevel(node.expression)
    }

    suspend fun add(expression: Expression): List<DataId> {
        enLock.readLock().withLock {
            return runBlocking {
                if (expression.isRoot()) {
                    return@runBlocking saveRoot(expression)
                }

                return@runBlocking saveExpression(expression)
            }
        }
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
        )
        nodeRepository.save(node)
        return result
    }

    private suspend fun saveRoot(expression: Expression): List<DataId> {
        if (nodeRepository.queryByOutput(expression.outputs[0]) == null) {
            val node = Node.makeRoot(expression)
            nodeRepository.save(node)
        }
        return listOf(expression.outputs[0])
    }

    private suspend fun tryBatch(node: Node, to: Pointer): BatchExpression {
        val batchList = ArrayList<Expression>()
        tryBatchInternal(batchList, node, to)
        return BatchExpression(batchList)
    }

    private tailrec suspend fun tryBatchInternal(
        batchList: ArrayList<Expression>, node: Node, to: Pointer
    ) {
        if (upstreamOneLevel(node).filter { it != node }.map { it.effectivePtr >= to }.fold(true, Boolean::and)) {
            val downstream = downstreamOneLevel(node)
            val first = downstream.first()
            batchList.add(node.expression)
            node.expectedPtr = to
            if (downstream.size == 1 && !node.mustCalculate) {
                tryBatchInternal(batchList, first, to)
            }
        }
    }

    private fun getRunRootState(node: Node) = runRootStates.computeIfAbsent(node.id) { RunRootState() }

    private fun getNodeLock(node: Node): Mutex {
        return getNodeLock(node.id)
    }

    private fun getNodeLock(nodeId: NodeId): Mutex {
        return nodeLocks[nodeId.hashCode() % MUTEX_SIZE]
    }

    private fun genId() = "__" + UUID.randomUUID().toString().replace("-", "")
    private suspend fun pushRunning(node: Node) {
        for (id in node.ids()) {
            messageQueue.pushRunning(id)
        }
    }

    private suspend fun pushFailed(node: Node, reason: String) {
        for (id in node.ids()) {
            messageQueue.pushRunFailed(id, reason)
        }
    }

    private suspend fun pushFinished(node: Node) {
        for (id in node.ids()) {
            messageQueue.pushRunFinish(id)
        }
    }

    private suspend fun pushSystemFailed(node: Node) {
        for (id in node.ids()) {
            messageQueue.pushSystemFailed(id)
        }
    }

}

