package infra.db.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.datetime.*
import model.*
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.LocalDateTime
import java.util.TreeMap

data class NodeDO(
    @BsonId val id: ObjectId,
    val valid: Boolean,
    val effectivePtr: Int,
    val expectedPtr: Int,
    val expression: ExpressionDO,
    val lastUpdateTime: LocalDateTime? = null,
    val isPerfCalculated: Boolean? = null
) {
    data class ExpressionDO(
        val inputs: List<String>,
        val outputs: List<String>,
        val funcId: String,
        val dataflow: String,
        val arguments: TreeMap<String, ArgumentDO>
    ) {
        data class ArgumentDO(val value: String, val type: String) {
            fun toModel(): Argument {
                return Argument(value, type)
            }
        }

        fun toModel(): Expression {
            return Expression(
                inputs.map { DataId(it) },
                outputs.map { DataId(it) },
                FuncId(funcId),
                dataflow,
                arguments.map { (k, v) -> k to v.toModel() }.toMap()
            )
        }

    }

    fun toModel(): Node {
        return Node(
            valid,
            Pointer(effectivePtr),
            Pointer(expectedPtr),
            isRunning = false,
            resetPtr = false,
            expression = expression.toModel(),
            lastUpdateTime = lastUpdateTime?.toKotlinLocalDateTime()?.toInstant(TimeZone.currentSystemDefault())
                ?: Instant.DISTANT_PAST,
            isPerfCalculated = isPerfCalculated ?: false
        )
    }
}

fun Node.toMongo(id: ObjectId): NodeDO {
    return NodeDO(
        id,
        valid,
        effectivePtr.value,
        expectedPtr.value,
        expression.toMongo(),
        lastUpdateTime.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime(),
        isPerfCalculated
    )
}

fun Expression.toMongo(): NodeDO.ExpressionDO {
    return NodeDO.ExpressionDO(
        inputs.map { it.str },
        outputs.map { it.str },
        funcId.value,
        dataflow,
        arguments.map { (k, v) -> k to v.toMongo() }.toMap(TreeMap())
    )
}

fun Argument.toMongo(): NodeDO.ExpressionDO.ArgumentDO {
    return NodeDO.ExpressionDO.ArgumentDO(value, type)
}


object MongoNodeRepository : NodeRepository {

    private const val NODES_TABLE = "nodes"

    override suspend fun save(node: Node): Node {
        val id = queryNodeDOByExpression(node.expression)?.id ?: ObjectId()
        val save = node.toMongo(id)
        MongoConnection.getCollection<NodeDO>(NODES_TABLE).replaceOne(
            eq("_${NodeDO::id.name}", id), save, ReplaceOptions().upsert(true)
        )
        return node
    }

    override suspend fun queryByExpression(expression: Expression): Node? {
        val nodeDO = queryNodeDOByExpression(expression) ?: return null
        return nodeDO.toModel()
    }

    override suspend fun queryByInput(id: DataId): Set<Node> {
        return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            `in`("${NodeDO::expression.name}.${NodeDO.ExpressionDO::inputs.name}", id.str)
        ).map { it.toModel() }.toSet()
    }

    override suspend fun queryByOutput(id: DataId): Node? {
        return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            `in`("${NodeDO::expression.name}.${NodeDO.ExpressionDO::outputs.name}", id.str)
        ).map { it.toModel() }.firstOrNull()
    }

    override suspend fun queryByFunc(funcId: FuncId): Set<Node> {
        return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::funcId.name}", funcId.value)
        ).map { it.toModel() }.toSet()
    }

    private suspend fun queryNodeDOByExpression(expression: Expression): NodeDO? {
        val edo = expression.toMongo()
        if (expression.isRoot()) {
            if (edo.outputs.size != 1) {
                throw Exception("Root node should have only one output")
            }
            return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
                eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::outputs.name}", edo.outputs)
            ).firstOrNull()
        } else {
            return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
                Filters.and(
                    listOf(
                        eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::inputs.name}", edo.inputs),
                        eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::funcId.name}", edo.funcId),
                        eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::dataflow.name}", edo.dataflow),
                        eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::arguments.name}", edo.arguments)
                    )
                )
            ).firstOrNull()
        }

    }

}