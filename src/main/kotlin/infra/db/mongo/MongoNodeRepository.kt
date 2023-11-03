package infra.db.mongo

import model.*

object MongoNodeRepository: NodeRepository {
    val nodeDatabase = MongoConnection.client.getDatabase("node")
//    val collection = database.getCollection<>(Constants.MONGODB_NODE_TABLE_NAME)
    override fun save(node: Node): Node {
        TODO("Not yet implemented")
    }

    override fun queryByExpression(expression: Expression): ExpressionNode {
        TODO("Not yet implemented")
    }


    override fun queryByInput(id: DataId): ExpressionNode {
        TODO("Not yet implemented")
    }

    override fun queryByOutput(id: DataId): ExpressionNode {
        TODO("Not yet implemented")
    }

    override fun queryRoot(id: DataId): RootNode {
        TODO("Not yet implemented")
    }
}