package model

import java.util.*


abstract class ExpressionNetwork(
    private val nodeRepository: NodeRepository,
    private val taskRepository: TaskRepository,
    private val dataManager: DataManager,
    private val executor: Executor
) {

    fun run(id: DataId) {
        val node = nodeRepository.queryByOutput(id)
        if (node.expression.inputs.isNotEmpty()) {
            runExpressionNode(node)
        } else {
            runRootNode(node)
        }
    }

    private fun runRootNode(node: Node) {
        node.effectivePtr = dataManager.findLastPtr(node.expression.outputs[0])
        node.expectedPtr = node.effectivePtr
        nodeRepository.save(node)
        runDownstream(node)
    }

    private fun runExpressionNode(node: Node) {
        if (!node.valid) return
        val task = Task(
            id = genId(),
            expression = node.expression
        )
        taskRepository.save(task)
        executor.run(node.expression, withId = task.id, from = node.expectedPtr, to = node.expectedPtr)
        node.isRunning = true
        nodeRepository.save(node)
    }

    private fun runDownstream(node: Node) {
        for (dNode in findDownstream(node.expression)) {
            dNode.expectedPtr = node.effectivePtr
            runExpressionNode(dNode)
        }
    }

    fun finishRun(id: TaskId) {
        val task = taskRepository.get(id)
        val node = nodeRepository.queryByOutput(task.expression.outputs[0])
        node.isRunning = false // lock
        if (node.toResetPtr) { // lock
            node.effectivePtr = Pointer.ZERO
            node.toResetPtr = false // lock
        } else {
            node.effectivePtr = node.expectedPtr
        }
        nodeRepository.save(node)
        taskRepository.delete(id)
        runDownstream(node)
    }

    fun failedRun(id: TaskId) {
        val task = taskRepository.get(id)
        val node = nodeRepository.queryByOutput(task.expression.outputs[0])
        taskRepository.delete(id)
        markNodeInvalid(node)
    }

    private fun markNodeInvalid(node: Node) {
        node.valid = false
        node.effectivePtr = Pointer.ZERO
        nodeRepository.save(node)
        for (dNode in findDownstream(node.expression)) {
            markNodeInvalid(dNode)
        }
    }

//    fun timeoutRun(id: TaskId) {
//        executor.tryCancel(id)
//        val task = taskRepository.get(id)
//        val node = nodeRepository.queryByOutput(task.expression.outputs[0])
//        executor.run()
//    }

    fun updateFunc(funcId: FuncId) {
        val node = nodeRepository.queryByFunc()
        if (node.isRunning) { // lock
            node.toResetPtr = true // lock
        } else {
            node.effectivePtr = Pointer.ZERO
        }
        nodeRepository.save(node)
    }

    fun findUpstream(expression: Expression): Set<Node> {
        val result = HashSet<Node>()
        for (input in expression.inputs) {
            result.add(nodeRepository.queryByOutput(input))
        }
        return result
    }


    fun findDownstream(expression: Expression): Set<Node> {
        val result = HashSet<Node>()
        for (input in expression.outputs) {
            result.add(nodeRepository.queryByOutput(input))
        }
        return result
    }

    fun add(expression: Expression): List<DataId> {
        val result = ArrayList<DataId>()
        for (output in expression.outputs) {
            result.add(genId())
        }
        expression.outputs = result
        val nodes = findUpstream(expression)
        var expectedPtr: Pointer = Pointer.MAX
        for (node in nodes) {
            expectedPtr = minOf(node.effectivePtr, expectedPtr)
        }
        val node = Node(
            effectivePtr = Pointer.ZERO,
            expectedPtr = expectedPtr,
            expression = expression,
            valid = true,
            isRunning = false,
            toResetPtr = false
        )
        nodeRepository.save(node) // 持久化
        return result
    }

    private fun genId() = UUID.randomUUID().toString()


}