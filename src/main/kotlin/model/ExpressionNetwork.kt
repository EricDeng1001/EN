package model


abstract class ExpressionNetwork(private val nodeRepository: NodeRepository) {

    fun run(id: DataId) {
        nodeRepository.queryByInput(id)
    }

    fun findUpstream(node: Node): Set<Node> {
        val result = HashSet<Node>()
        for (input in node.expression.inputs) {
            nodeRepository.queryByOutput(input)
        }
        return result
    }

    fun findDownstream(node: Node): Set<Node> {
        TODO()
    }

    fun add(expression: Expression): Set<DataId> {
        TODO()
    }

    fun updateFunc(funcId: FuncId) {}
}