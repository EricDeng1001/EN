package model

abstract class ExpressionNetwork(private val nodeRepository: NodeRepository) {

    fun run(id: DataId) {
        nodeRepository.queryByInput(id)
    }

    fun findUpstream(id: DataId): Set<Node> {
        TODO()
    }

    fun findDownstream(id: DataId): Set<Node> {
        TODO()
    }

    fun add(expression: Expression): Set<DataId> {
        TODO()
    }

    fun updateFunc(funcId: FuncId) {}
}