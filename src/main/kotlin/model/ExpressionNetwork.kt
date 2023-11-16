package model

import kotlinx.coroutines.sync.Mutex
import java.util.*
import java.util.concurrent.ConcurrentHashMap


abstract class ExpressionNetwork(
    private val nodeRepository: NodeRepository,
    private val taskRepository: TaskRepository,
    private val executor: Executor,
    private val messageQueue: MessageQueue
) {
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
        try {
            mutex.lock()
            val loadedNode = loadedNodes[it.id]
            if (loadedNode != null) {
                it = loadedNode
            } else {
                loadedNodes[it.id] = it
            }
            return it
        } finally {
            mutex.unlock()
        }
    }

    suspend fun runRoot(id: DataId, effectivePtr: Pointer) {
        val node = getNode(id) ?: return
        if (node.expression.isRoot()) {
            runRootNode(node, effectivePtr)
        }
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
        try {
            mutex.lock()
            if (node.isRunning) { // somehow double run, doesn't matter, we can safe ignore this
                return
            }
            if (node.shouldRun()) {
                val task = Task(
                    id = genId(),
                    expression = node.expression
                )
                taskRepository.save(task)
                executor.run(node.expression, withId = task.id, from = node.effectivePtr, to = node.expectedPtr)
                node.isRunning = true
                for (id in node.ids()) {
                    messageQueue.pushRunning(id)
                }
            } else { // somehow the node is not suppose to run, end this run
                for (id in node.ids()) {
                    messageQueue.pushRunFinish(id)
                }
                endRun(node)
            }
        } finally {
            mutex.unlock()
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
        val task = taskRepository.get(id) ?: return
        val node = getNode(task.expression.outputs[0])!!
        val mutex = locks[node.id]!!
        mutex.lock()
        node.isRunning = false
        if (!node.resetPtr) { // a success run
            try {
                node.effectivePtr = node.expectedPtr
                nodeRepository.save(node)
            } finally {
                mutex.unlock()
            }
            updateDownstream(node)
        } else {
            // should abort this run
            mutex.unlock()
        }
        endRun(node)
        taskRepository.delete(id)
        for (id in node.ids()) {
            messageQueue.pushRunFinish(id)
        }
    }

    private fun endRun(node: Node) {
        locks.remove(node.id) // always release lock's mem when finish running
        loadedNodes.remove(node.id)
    }

    suspend fun failedRun(id: TaskId) {
        val task = taskRepository.get(id) ?: return
        taskRepository.delete(id)
        val node = loadedNodes[Node.Id(task.expression.outputs[0])]!!
        for (id in node.ids()) {
            messageQueue.pushRunFailed(id)
        }
        markInvalid(node)
    }

    // end 2
    private suspend fun markInvalid(node: Node) {
        val mutex = locks[node.id]!!
        mutex.lock()
        endRun(node)
        node.valid = false
        node.effectivePtr = Pointer.ZERO
        nodeRepository.save(node)
        mutex.unlock()
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
        mutex.lock()
        if (node.isRunning) {
            node.resetPtr = true
        }
        node.effectivePtr = Pointer.ZERO
        node.valid = true
        nodeRepository.save(node)
        mutex.unlock()

        for (dNode in loadDownstream(node.expression)) {
            markNeedUpdate(dNode)
        }
    }


    suspend fun loadUpstream(expression: Expression): Set<Node> {
        val result = HashSet<Node>()
        for (input in expression.inputs) {
            result.add(getNode(input) ?: continue)
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

    private suspend fun ExpressionNetwork.saveExpression(expression: Expression): List<DataId> {
        val queryByExpression = nodeRepository.queryByExpression(expression)
        if (queryByExpression != null) return queryByExpression.expression.outputs

        for (input in expression.inputs) {
            if (getNode(input) == null) { // invalid expression ref unknown data id
                throw IllegalArgumentException("ref $input is unknown")
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

    private fun genId() = UUID.randomUUID().toString()


}