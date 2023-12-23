package infra.db.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import model.*
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.util.TreeMap

data class NodeDO(
    @BsonId val mongoId: ObjectId? = null,
    val id: String,
    val valid: Boolean,
    val effectivePtr: Int,
    val expectedPtr: Int,
    val expression: ExpressionDO,
    val isPerfCalculated: Boolean? = null,
    val mustCalculate: Boolean? = null,
    val runRoot: String? = null
) {
    data class InputDO(
        val type: String,
        val ids: List<String>
    ) {
        fun toModel(): Input {
            return Input(
                type = InputType.valueOf(type),
                ids = ids.map { DataId(it) }
            )
        }
    }

    data class ExpressionDO(
        val inputs: List<InputDO>,
        val inputsFlat: List<String>,
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
                inputs.map { it.toModel() },
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
            expression = expression.toModel(),
            isPerfCalculated = isPerfCalculated ?: false,
            mustCalculate = mustCalculate ?: false,
            runRoot = NodeId(runRoot ?: "")
        )
    }
}

fun Node.toMongo(): NodeDO {
    return NodeDO(
        null,
        id.str,
        valid,
        effectivePtr.value,
        expectedPtr.value,
        expression.toMongo(),
        isPerfCalculated,
        mustCalculate = mustCalculate
    )
}

fun Input.toMongo(): NodeDO.InputDO {
    return NodeDO.InputDO(
        type = type.name,
        ids = ids.map { it.str }
    )
}

fun Expression.toMongo(): NodeDO.ExpressionDO {
    return NodeDO.ExpressionDO(
        inputs.map { it.toMongo() },
        inputs.flatMap { it.toMongo().ids },
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
        val save = node.toMongo()
        MongoConnection.getCollection<NodeDO>(NODES_TABLE).replaceOne(
            eq("id", node.id), save, ReplaceOptions().upsert(true)
        )
        return node
    }

    override suspend fun saveAll(nodes: Iterable<Node>) {
        val operations = nodes.map { ReplaceOneModel(eq("id", it.id), it.toMongo(), ReplaceOptions().upsert(true)) }
        MongoConnection.getCollection<NodeDO>(NODES_TABLE).bulkWrite(operations)
    }

    override suspend fun queryByExpression(expression: Expression): Node? {
        val nodeDO = queryNodeDOByExpression(expression) ?: return null
        return nodeDO.toModel()
    }

    override suspend fun queryByInput(id: DataId): List<Node> {
        return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            `in`("${NodeDO::expression.name}.${NodeDO.ExpressionDO::inputsFlat.name}", id.str)
        ).map { it.toModel() }.toList()
    }

    override suspend fun queryByOutput(id: DataId): Node? {
        return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            `in`("${NodeDO::expression.name}.${NodeDO.ExpressionDO::outputs.name}", id.str)
        ).map { it.toModel() }.firstOrNull()
    }

    override suspend fun queryByFunc(funcId: FuncId): List<Node> {
        return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::funcId.name}", funcId.value)
        ).map { it.toModel() }.toList()
    }

    override suspend fun queryAllRoot(): List<Node> {
        return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            size("${NodeDO::expression.name}.${NodeDO.ExpressionDO::outputs.name}", 0)
        ).map { it.toModel() }.toList()
    }

    override suspend fun queryAllNonRoot(): List<Node> {
        return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            not(size("${NodeDO::expression.name}.${NodeDO.ExpressionDO::outputs.name}", 0))
        ).map { it.toModel() }.toList()
    }

    override suspend fun get(id: NodeId): Node? {
        return MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            eq("id", id.str)
        ).map { it.toModel() }.firstOrNull()
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
                and(
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