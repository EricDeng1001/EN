package model

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

abstract class ExpressionNetwork(
    private val nodeRepository: NodeRepository,
    private val taskRepository: TaskRepository,
    private val executor: Executor,
    private val messageQueue: MessageQueue,
    private val performanceService: PerformanceService,
    private val symbolLibraryService: SymbolLibraryService
) {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass.name)
    private val states: MutableMap<NodeId, NodeState> = ConcurrentHashMap()

    private val MUTEX_SIZE: Int = 1024
    private val nodeLocks: Array<Mutex> = Array(MUTEX_SIZE) { Mutex() }
    private val tryRunTasksQueue = ConcurrentHashMap.newKeySet<NodeId>(32)

    @OptIn(DelicateCoroutinesApi::class)
    private val dispatcher = newFixedThreadPoolContext(Runtime.getRuntime().availableProcessors(), "en-background")
    private val backgroundTasks = CoroutineScope(dispatcher)

    private suspend fun getNode(id: SymbolId): Node? {
        return nodeRepository.queryByOutput(id)
    }

    private suspend fun getNode(id: NodeId): Node? {
        return nodeRepository.get(id)
    }

    private suspend fun findNodeByInput(id: SymbolId): List<Node> {
        return nodeRepository.queryByInput(id)
    }

    suspend fun markMustCalc(ids: List<SymbolId>) {
        val changed = ArrayList<Node>(ids.size)
        for (id in ids) {
            val node = getNode(id) ?: continue
            node.mustCalculate = true
            changed.add(node)
        }
        nodeRepository.saveAll(changed)
    }

    suspend fun markShouldUpdate(ids: List<SymbolId>, shouldUpdate: Boolean) {
        val changed = ArrayList<Node>(ids.size)
        for (id in ids) {
            val node = getNode(id) ?: continue
            node.shouldUpdate = shouldUpdate
            changed.add(node)
        }
        nodeRepository.saveAll(changed)
    }

    suspend fun buildGraph(ids: List<SymbolId>): GraphView {
        val nodes: MutableList<Node> = ArrayList()
        for (id in ids) {
            val node = getNode(id) ?: continue
            nodes.add(node)
        }
        return Graph(nodes, emptyList()).view()
    }

    suspend fun buildDebugGraph(ids: List<SymbolId>): GraphDebugView {
        val nodes: MutableList<Node> = ArrayList()
        val inputs = HashSet<SymbolId>()
        for (id in ids) {
            val node = getNode(id) ?: continue
            nodes.add(node)

            inputs.addAll(node.expression.inputs.flatMap { it.ids }.toList())
        }
        val allInputs = inputs - ids.toSet()

        val inputsNode = ArrayList<Node>()
        for (id in allInputs) {
            val node = getNode(id) ?: continue
            inputsNode.add(node)
        }

        return Graph(nodes, inputsNode).debugView()
    }

    suspend fun queryExpressionsState(ids: List<SymbolId>): List<Pair<SymbolId, String?>> {
        val res: MutableList<Pair<SymbolId, String?>> = mutableListOf()
        for (id in ids) {
            val state = states[NodeId(id.str)]
            if (state == null) {
                val node = getNode(id)
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
                    val node = getNode(id)
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

    suspend fun updateRoot(id: SymbolId, effectivePtr: Pointer) {
        logger.debug("update root: {} to {}", id, effectivePtr)
        val node = getNode(id) ?: return
        if (node.expression.isRoot()) {
            if (effectivePtr > node.effectivePtr) {
                node.effectivePtr = effectivePtr
                node.expectedPtr = effectivePtr
                nodeRepository.save(node)
                updateRootSafeAsync(node)
            }
        }
    }

    private suspend fun updateRootSafeAsync(node: Node) = updateDownstream(node)
    suspend fun reUpdateRoot(id: SymbolId, resetPtr: Pointer) {
        val node = getNode(id) ?: return
        if (node.expression.isRoot()) {
            val save = node.effectivePtr
            val all = downstreamAllIncludeSelf(node) {
                it.effectivePtr = resetPtr
            }
            nodeRepository.saveAll(all)
            node.effectivePtr = save
            updateRootSafeAsync(node)
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

    suspend fun markForceRunPerf(id: SymbolId) {
        val node = getNode(id) ?: return
        node.isPerfCalculated = false
        nodeRepository.save(node)
    }

    suspend fun runExpression(id: SymbolId) {
        val node = getNode(id) ?: return
        if (!node.shouldUpdate) {
            backgroundTasks.launch {
                tryRunExpressionNode(node)
            }
        }
    }

    suspend fun forceRun(id: SymbolId) {
        val node = getNode(id) ?: return
        tryRunExpressionNode(node)
    }

    private suspend fun updateDownstream(root: Node) {
        val nextLevel = downstreamOneLevel(root)
        val idList = nextLevel.map {
            it.expression.outputs[0].str
        }.toList()
        logger.debug("{} downstream size: {}, {}", root.idStr, idList.size, idList)

        for (next in nextLevel) {
            if (root.shouldUpdate == next.shouldUpdate) {
                logger.debug("{} downstream update: {}", root.idStr, next.idStr)
                backgroundTasks.launch {
                    tryRunExpressionNode(next)
                }
            }
        }

    }

    private suspend fun tryRunExpressionNode(node: Node) {
        try {
            logger.debug("{} enter tryRunExpressionNode", node.idStr)

            val mutex = getNodeLock(node)
            mutex.withLock {
                if (states[node.id] == NodeState.RUNNING) {
                    logger.debug("{} already running", node.idStr)
                    if (!tryRunTasksQueue.add(node.id)) logger.debug("{} already in task queue", node.idStr)
                    logger.debug("{} add to task queue", node.idStr)
                    return
                }

                if (!node.valid) {
                    logger.debug("{} invalid", node.idStr)
                    pushFailed(node, "expression not valid due to upstream invalid or self invalid")
                    return
                }

                val newPtr = findExpectedPtr(node)
                if (node.expectedPtr != newPtr) {
                    node.expectedPtr = newPtr
                    nodeRepository.save(node)
                } else {
                    logger.debug("{} expected ptr {} not changed", node.idStr, newPtr.value)
                }

                if (!node.shouldRun()) {
                    logger.debug("{} should not run", node.idStr)
                    return
                }

                val priority: Int = if (node.shouldUpdate) 1 else 0

                val task = Task(
                    id = genId(),
                    expression = node.expression,
                    start = Clock.System.now(),
                    from = node.effectivePtr,
                    to = node.expectedPtr,
                    priority = priority
                )
                try {
                    logger.info("try to run expression node: $task")
                    val started =
                        executor.run(task)
                    logger.debug("start to run expression node: {}", task)
                    if (started) {
                        states[node.id] = NodeState.RUNNING
                        pushRunning(node)
                        taskRepository.save(task)
                    } else {
                        task.failedReason = "no slot to output"
                        taskRepository.save(task)
                    }
                } catch (e: Exception) {
                    logger.error("try to run expression node err: ${task.id} $e")
                    states[node.id] = NodeState.SYSTEM_FAILED
                    systemFailed(task, node, e.message.toString())
                }
            }
        } catch (e: Exception) {
            logger.error("${node.idStr} tryRunExpressionNode error: $e")
        }
    }

    private fun getPreTimePointer(pointer: Pointer, offset: Int, freq: Int): Pointer {
        val preTimePoint = ((pointer.value - offset) / freq) * freq + offset
        return Pointer(preTimePoint)
    }

    private suspend fun normalizePointer(id: SymbolId, pointer: Pointer): Pointer {
        return try {
            symbolLibraryService.getSymbol(id).let { symbol ->
                getPreTimePointer(pointer, symbol.offsetValue, symbol.frequencyValue)
            }
        } catch (e: Exception) {
            logger.error("Error normalizing pointer: ${e.message}")
            throw e
        }
    }

    private suspend fun findExpectedPtr(node: Node): Pointer {
        val nodes = upstreamOneLevel(node)
        var expectedPtr: Pointer = Pointer.MAX
        for (it in nodes) {
            expectedPtr = minOf(it.effectivePtr, expectedPtr)
        }
        return normalizePointer(node.expression.outputs[0], expectedPtr)
    }

    // end 1
    suspend fun succeedRun(id: TaskId) {
        logger.info("succeed run: $id")
        val task = taskRepository.get(id) ?: return
        backgroundTasks.launch {
            val node = getNode(task.nodeId)!!
            var run = false
            val mutex = getNodeLock(node)
            mutex.withLock {
                states[node.id] = NodeState.FINISHED
                if (tryRunTasksQueue.contains(node.id)) {
                    tryRunTasksQueue.remove(node.id)
                    logger.debug("start to try run task queue node: {}", node.idStr)
                    run = true
                }
            }
            node.effectivePtr = task.to
            task.finish = Clock.System.now()
            tryCalcPerf(node)
            nodeRepository.save(node)
            taskRepository.save(task)

            pushFinished(node)

            updateDownstream(node)

            if (run) {
                tryRunExpressionNode(node)
            }
        }
    }

    suspend fun failedRun(id: TaskId, reason: String) {
        logger.info("failed run: $id")
        val task = taskRepository.get(id) ?: return
        backgroundTasks.launch {
            task.finish = Clock.System.now()
            task.failedReason = reason
            taskRepository.save(task)
            val node = getNode(task.nodeId)!!
            val mutex = getNodeLock(node)
            mutex.withLock {
                states[node.id] = NodeState.FAILED
                tryRunTasksQueue.remove(node.id)
                logger.debug("remove queue node : {}", node.idStr)
            }
            pushFailed(node, reason)
            downstreamAllIncludeSelf(node) {
                markInvalid(it)
            }
        }
    }

    suspend fun systemFailedRun(id: TaskId, reason: String) {
        logger.info("system failed run: $id")
        val task = taskRepository.get(id) ?: return
        val node = getNode(task.nodeId)!!
        val mutex = getNodeLock(node)
        mutex.withLock {
            states[node.id] = NodeState.SYSTEM_FAILED
            tryRunTasksQueue.remove(node.id)
            logger.debug("remove queue node : {}", node.idStr)
        }
        systemFailed(task, node, reason)
    }

    private suspend fun tryCalcPerf(node: Node): Boolean {
        if (!node.isPerfCalculated) {
            try {
                for (output in node.expression.outputs) {
                    performanceService.calculate(output)
                }
                node.isPerfCalculated = true
                return true
            } catch (e: Exception) {
                logger.error("calculate performance error: $e")
            }
        }
        return false
    }

    private suspend fun systemFailed(task: Task, node: Node, reason: String) {
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
        backgroundTasks.launch {
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


    private suspend fun upstreamOneLevel(node: Node): Iterable<Node> {
        return nodeRepository.upstream1Lvl(node)
    }

    private suspend fun downstreamOneLevel(node: Node): Iterable<Node> {
        return nodeRepository.downstream1Lvl(node)
    }
    suspend fun allUpstreamNodeBesidesRoot(id: SymbolId): List<String> {
        val node = getNode(id)
        val set = HashSet<Node>()
        if (node != null) {
            set.add(node)
        } else {
            throw Error("origin node $id is null")
        }
        val all = upstreamAllNodes(set)
        logger.debug("allUpstreamNode: {}", all)
        return all.filter { it.depth != 0 }.map { it.idStr }.toList()
    }

    private suspend fun upstreamAllNodes(originSet: HashSet<Node>): Set<Node> {
//       非递归缓存去重会更快
        if (originSet.isEmpty()) {
            return originSet
        }
        val nodeSet = HashSet<Node>()
        originSet.forEach { node ->
            val nodes = upstreamOneLevel(node)
            nodes.forEach {
                nodeSet.add(it)
            }
        }

        val findNodes = upstreamAllNodes(nodeSet)
        findNodes.forEach {
            originSet.add(it)
        }
        return originSet
    }

    private suspend fun downstreamOneLevel(expression: Expression): List<Node> {
        val result = ArrayList<Node>()
        for (input in expression.outputs) {
            result.addAll(findNodeByInput(input))
        }
        return result
    }

    suspend fun add(expression: Expression): List<SymbolId> {
        return runBlocking {
            if (expression.isRoot()) {
                return@runBlocking saveRoot(expression)
            }

            return@runBlocking saveExpression(expression)
        }
    }


    private suspend fun saveExpression(expression: Expression): List<SymbolId> {
        val queryByExpression = nodeRepository.queryByExpression(expression)
        if (queryByExpression != null) return queryByExpression.expression.outputs

        for (input in expression.inputs) {
            for (id in input.ids) {
                if (getNode(id) == null) { // invalid expression ref unknown data id
                    throw IllegalArgumentException("ref $input is unknown")
                }
            }
        }

        val result = ArrayList<SymbolId>()
        for (output in expression.outputs) {
            result.add(SymbolId(genId()))
        }
        expression.outputs = result
        val node = Node(
            effectivePtr = Pointer.ZERO,
            expectedPtr = Pointer.ZERO,
            expression = expression,
            valid = true,
            depth = 0
        )
        node.depth = findDepth(node)
        nodeRepository.save(node)
        return result
    }

    private suspend fun saveRoot(expression: Expression): List<SymbolId> {
        if (getNode(expression.outputs[0]) == null) {
            val node = Node.makeRoot(expression)
            nodeRepository.save(node)
        }
        return listOf(expression.outputs[0])
    }

    private suspend fun findDepth(node: Node): Int {
        return upstreamOneLevel(node).maxOf { it.depth } + 1
    }

    suspend fun calcDepthForAll() {
        val roots = nodeRepository.queryAllRoot()
        for (root in roots) {
            root.depth = -1
            markDepth(root, 0)
        }

    }

    private suspend fun markDepth(root: Node, i: Int) {
        if (root.depth >= i) { // root and it's children's depth is already bigger
            return
        }
        root.depth = i
        nodeRepository.save(root)
        for (n in downstreamOneLevel(root)) {
            markDepth(n, i + 1)
        }
    }

//    private suspend fun tryBatch(node: Node, to: Pointer): BatchExpression {
//        val batchList = ArrayList<Expression>()
//        tryBatchInternal(batchList, node, to)
//        return BatchExpression(batchList)
//    }
//
//    private tailrec suspend fun tryBatchInternal(
//        batchList: ArrayList<Expression>, node: Node, to: Pointer
//    ) {
//        if (upstreamOneLevel(node).filter { it != node }.map { it.effectivePtr >= to }.fold(true, Boolean::and)) {
//            val downstream = downstreamOneLevel(node)
//            val first = downstream.first()
//            batchList.add(node.expression)
//            node.expectedPtr = to
//            if (downstream.size == 1 && !node.mustCalculate) {
//                tryBatchInternal(batchList, first, to)
//            }
//        }
//    }

    private fun getNodeLock(node: Node): Mutex {
        return getNodeLock(node.id)
    }

    private fun getNodeLock(nodeId: NodeId): Mutex {
        return nodeLocks[nodeId.hashCode().absoluteValue % MUTEX_SIZE]
    }

    suspend fun rerun(id: TaskId) {
        val task = taskRepository.get(id) ?: return
        taskRepository.delete(id)
        val newTask = Task(
            id = genId(), expression = task.expression, start = Clock.System.now(), from = task.from, to = task.to
        )
        executor.run(newTask)
    }

    suspend fun forceRerun(id: SymbolId) {
        val node = getNode(id) ?: return
        val newTask = Task(
            id = genId(),
            expression = node.expression,
            start = Clock.System.now(),
            from = Pointer.ZERO,
            to = node.expectedPtr
        )
        executor.run(newTask)
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

    suspend fun getTasksByDataId(ids: List<SymbolId>): List<Task> {
        val tasks = ids.mapNotNull { getTaskByDataId(it) }.toList()
        logger.info("find ${tasks.size} tasks by taskIds: $ids")
        return tasks
    }

    suspend fun getTaskByDataId(id: SymbolId): Task? {
        return taskRepository.getTaskByDataId(id)
    }

    suspend fun getTaskByDataIdAndTo(id: SymbolId, to: Pointer): Task? {
        return taskRepository.getTaskByDataIdAndTo(id, to)
    }

    suspend fun setEff0Exp0(ids: List<SymbolId>, eff: Pointer, exp: Pointer) {
        val res = mutableListOf<Node>()
        for (id in ids) {
            val node = getNode(id) ?: continue
            node.effectivePtr = eff
            node.expectedPtr = exp
            res.add(node)
        }
        nodeRepository.saveAll(res)
    }

    suspend fun forceUpdateRoot(ids: List<SymbolId>) {
        for (id in ids) {
            val node = getNode(id) ?: continue
            updateRootSafeAsync(node)
        }
    }
}

