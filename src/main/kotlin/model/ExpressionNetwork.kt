package model

class ExpressionNetwork private constructor(private val nodeRepository: NodeRepository) {
    companion object {

        @Volatile
        private var INSTANCE: ExpressionNetwork? = null

        fun getInstance(nodeRepository: NodeRepository): ExpressionNetwork =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExpressionNetwork(nodeRepository)
            }
    }

    fun run(id: DataId) {
        TODO()
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