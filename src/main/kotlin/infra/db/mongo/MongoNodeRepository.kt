package infra.db.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.runBlocking
import model.*
import org.bson.types.ObjectId

object MongoNodeRepository : NodeRepository {

    private val collection = MongoConnection.defaultDatabase.getCollection<NodeDO>("nodes")
    private val translator = MongoNodeTranslator

    override suspend fun save(node: Node): Node {
        val id = queryNodeDOByExpression(node.expression)?.id ?: ObjectId()
        val save = translator.toMongo(node, id)
        collection.replaceOne(
            eq("_${NodeDO::id.name}", id), save, ReplaceOptions().upsert(true)
        )
        return node
    }

    override suspend fun queryByExpression(expression: Expression): Node? {
        val nodeDO = queryNodeDOByExpression(expression) ?: return null
        return translator.toModel(nodeDO)
    }


    override suspend fun queryByInput(id: DataId): Set<Node> {
        return collection.find<NodeDO>(
            `in`("${NodeDO::expression.name}.${NodeDO.ExpressionDO::inputs.name}", id.str)
        ).map { translator.toModel(it) }.toSet()
    }

    override suspend fun queryByOutput(id: DataId): Node? {
        return collection.find<NodeDO>(
            `in`("${NodeDO::expression.name}.${NodeDO.ExpressionDO::outputs.name}", id.str)
        ).map { translator.toModel(it) }.firstOrNull()
    }

    override suspend fun queryByFunc(funcId: FuncId): Set<Node> {
        return collection.find<NodeDO>(
            eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::funcId.name}", funcId.value)
        ).map { translator.toModel(it) }.toSet()
    }

    private suspend fun queryNodeDOByExpression(expression: Expression): NodeDO? {
        val edo = translator.toMongo(expression)
        if (expression.isRoot()) {
            if (edo.outputs.size != 1) {
                throw Exception("Root node should have only one output")
            }
            return collection.find<NodeDO>(
                eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::outputs.name}", edo.outputs)
            ).firstOrNull()
        } else {
            return collection.find<NodeDO>(
                Filters.and(
                    listOf(
                        eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::inputs.name}", edo.inputs),
                        eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::funcId.name}", edo.funcId),
                        eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::shapeRule.name}", edo.shapeRule),
                        eq(
                            "${NodeDO::expression.name}.${NodeDO.ExpressionDO::alignmentRule.name}",
                            edo.alignmentRule
                        ),
                        eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::arguments.name}", edo.arguments)
                    )
                )
            ).firstOrNull()
        }

    }

}