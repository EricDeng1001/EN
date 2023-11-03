package infra.db.mongo

import com.mongodb.reactivestreams.client.MongoDatabase
import model.*
import web.Constants

class NodeMongoRepository(database: MongoDatabase): NodeRepository {
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