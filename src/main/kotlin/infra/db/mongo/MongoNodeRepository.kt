package infra.db.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.runBlocking
import model.*
import org.bson.types.ObjectId

object MongoNodeRepository : NodeRepository {

    private val collection = MongoConnection.defaultDatabase.getCollection<NodeDO>("nodes")
    private val translator = MongoNodeTranslator

    override fun save(node: Node): Node {
        val id = queryNodeDOByExpression(node.expression)?.id ?: ObjectId()
        val save = translator.toMongo(node, id)
        runBlocking {
            collection.insertOne(save)
        }
        return node
    }

    override fun queryByExpression(expression: Expression): Node? {
        val nodeDO = queryNodeDOByExpression(expression) ?: return null
        return translator.toModel(nodeDO)
    }


    override fun queryByInput(id: DataId): Set<Node> {
        return runBlocking {
            collection.find<NodeDO>(
                Filters.`in`(Expression::inputs.name, id)
            ).map { translator.toModel(it) }.toSet()
        }
    }

    override fun queryByOutput(id: DataId): Node? {
        return runBlocking {
            collection.find<NodeDO>(
                eq(Expression::outputs.name, id)
            ).map { translator.toModel(it) }.firstOrNull()
        }
    }

    private fun queryNodeDOByExpression(expression: Expression): NodeDO? {
        val edo = translator.toMongo(expression)
        if (expression.isRoot()) {
            if (edo.outputs.size != 1) {
                throw Exception("Root node should have only one output")
            }
            return runBlocking {
                collection.find<NodeDO>(
                    eq(Expression::outputs.name, edo.outputs)
                ).firstOrNull()
            }
        } else {
            return runBlocking {
                collection.find<NodeDO>(
                    Filters.and(
                        listOf(
                            eq(Expression::inputs.name, edo.inputs),
                            eq(Expression::funcId.name, edo.funcId),
                            eq(Expression::shapeRule.name, edo.shapeRule),
                            eq(Expression::alignmentRule.name, edo.alignmentRule),
                            eq(Expression::arguments.name, edo.arguments)
                        )
                    )
                ).firstOrNull()
            }
        }

    }

}