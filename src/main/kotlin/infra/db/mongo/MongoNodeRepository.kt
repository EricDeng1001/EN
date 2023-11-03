package infra.db.mongo

import model.DataId
import model.Expression
import model.Node
import model.NodeRepository

object MongoNodeRepository : NodeRepository {
    val nodeDatabase = MongoConnection.client.getDatabase("node")

    //    val collection = database.getCollection<>(Constants.MONGODB_NODE_TABLE_NAME)
    override fun save(node: Node): Node {
        TODO("Not yet implemented")
    }

    override fun queryByExpression(expression: Expression): Node {
        TODO("Not yet implemented")
    }


    override fun queryByInput(id: DataId): Node {
        TODO("Not yet implemented")
    }

    override fun queryByOutput(id: DataId): Node {
        TODO("Not yet implemented")
    }
}