package infra.db.mongo

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import model.*
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.util.*
import kotlin.collections.ArrayList

data class NodeDO(
    @BsonId val mongoId: ObjectId? = null,
    val id: String,
    val valid: Boolean,
    val effectivePtr: Int,
    val expectedPtr: Int,
    val expression: ExpressionDO,
    val isPerfCalculated: Boolean? = null,
    val mustCalculate: Boolean? = null,
    val shouldUpdate: Boolean? = null,
    var depth: Int? = null
) {
    fun toModel(): Node {
        return Node(
            valid,
            Pointer(effectivePtr),
            Pointer(expectedPtr),
            expression = expression.toModel(),
            isPerfCalculated = isPerfCalculated ?: false,
            mustCalculate = mustCalculate ?: false,
            shouldUpdate = shouldUpdate ?: false,
            depth = depth ?: 0
        )
    }

    data class InputDO(
        val type: String,
        val ids: List<String>
    ) {
        fun toModel(): Input {
            return Input(
                type = InputType.valueOf(type),
                ids = ids.map { SymbolId(it) }
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
                outputs.map { SymbolId(it) },
                FuncId(funcId),
                dataflow,
                arguments.map { (k, v) -> k to v.toModel() }.toMap()
            )
        }
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
        mustCalculate = mustCalculate,
        shouldUpdate = shouldUpdate,
        depth = depth
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


data class NodeCache(
    var node: Node,
    var upstream: List<Node>? = null,
    var downstream: List<Node>? = null
)

object MongoNodeRepository : NodeRepository {

    private const val NODES_TABLE = "nodes"

    private val cache: Cache<String, NodeCache> = Caffeine.newBuilder().maximumSize(1_000_000).build()

    private fun saveToCache(node: Node): NodeCache {
        val nodeCache = cache.get(node.id.str) { NodeCache(node) }
        nodeCache.node = node
        return nodeCache
    }

    private fun saveToCache(nodes: Iterable<Node>) {
        nodes.forEach {
            cache.put(it.id.str, NodeCache(it))
        }
    }

    private fun getCache(id: SymbolId): NodeCache? {
        return cache.getIfPresent(id.str)
    }

    private fun getCache(id: NodeId): NodeCache? {
        return cache.getIfPresent(id.str)
    }

    override suspend fun save(node: Node): Node {
        val save = node.toMongo()
        MongoConnection.getCollection<NodeDO>(NODES_TABLE).replaceOne(
            eq("id", node.idStr), save, ReplaceOptions().upsert(true)
        )
        saveToCache(node)
        return node
    }

    override suspend fun saveAll(nodes: Iterable<Node>) {
        val operations = nodes.map { ReplaceOneModel(eq("id", it.idStr), it.toMongo(), ReplaceOptions().upsert(true)) }
        if (operations.isNotEmpty()) {
            MongoConnection.getCollection<NodeDO>(NODES_TABLE).bulkWrite(operations)
        }
        saveToCache(nodes)
    }


    override suspend fun queryByExpression(expression: Expression): Node? {
        val nodeDO = queryNodeDOByExpression(expression) ?: return null
        val node = nodeDO.toModel()
        saveToCache(node)
        return node
    }

    override suspend fun downstream1Lvl(node: Node): List<Node> {
//        var nodeCache = getCache(node.id)
//        if (nodeCache != null) {
//            if (nodeCache.downstream != null) {
//                return nodeCache.downstream!!
//            }
//        } else {
//            nodeCache = saveToCache(node)
//        }
        val nodes = queryByInput(node.expression.outputs)
//        nodeCache.downstream = nodes
        return nodes
    }

    override suspend fun upstream1Lvl(node: Node): Iterable<Node> {
//        var nodeCache = getCache(node.id)
//        if (nodeCache != null) {
//            if (nodeCache.upstream != null) {
//                return nodeCache.upstream!!
//            }
//        } else {
//            nodeCache = saveToCache(node)
//        }

        val nodes = queryByOutput(node.expression.inputs.flatMap { it.ids })

//        nodeCache.upstream = nodes
        return nodes
    }

    override suspend fun queryByInput(id: SymbolId): List<Node> {
        val nodes = MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            `in`("${NodeDO::expression.name}.${NodeDO.ExpressionDO::inputsFlat.name}", id.str)
        ).map { it.toModel() }.toList()
        saveToCache(nodes)
        return nodes
    }

    override suspend fun queryByInput(id: List<SymbolId>): List<Node> {
        val nodes = MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            `in`("${NodeDO::expression.name}.${NodeDO.ExpressionDO::inputsFlat.name}", id.map { it.str })
        ).map { it.toModel() }.toList()
        saveToCache(nodes)
        return nodes
    }

    override suspend fun queryByOutput(id: SymbolId): Node? {
        return getCache(id)?.node ?: run {
            val node = MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
                `in`("${NodeDO::expression.name}.${NodeDO.ExpressionDO::outputs.name}", id.str)
            ).map { it.toModel() }.firstOrNull()
            if (node != null) {
                saveToCache(node)
            }
            return node
        }
    }

    override suspend fun queryByOutput(ids: List<SymbolId>): List<Node> {
        val cachedId = ArrayList<SymbolId>()
        val queryIds = ArrayList<SymbolId>()
        val result = ArrayList<Node>()
        ids.forEach {
            val nodeCache = getCache(it)
            if (nodeCache == null) {
                queryIds.add(it)
            } else {
                if (!result.contains(nodeCache.node)) {
                    result.add(nodeCache.node)
                }
            }
        }

        val nodes = MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            `in`("${NodeDO::expression.name}.${NodeDO.ExpressionDO::outputs.name}", queryIds.map { it.str })
        ).map { it.toModel() }.toList()
        saveToCache(nodes)
        result.addAll(nodes)
        return result
    }

    override suspend fun queryByFunc(funcId: FuncId): List<Node> {
        val nodes = MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            eq("${NodeDO::expression.name}.${NodeDO.ExpressionDO::funcId.name}", funcId.value)
        ).map { it.toModel() }.toList()
        saveToCache(nodes)
        return nodes
    }

    override suspend fun queryAllRoot(): List<Node> {
        val nodes = MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            size("${NodeDO::expression.name}.${NodeDO.ExpressionDO::inputsFlat.name}", 0)
        ).map { it.toModel() }.toList()
        saveToCache(nodes)

        return nodes
    }

    override suspend fun queryAllNonRoot(): List<Node> {
        val nodes = MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
            not(size("${NodeDO::expression.name}.${NodeDO.ExpressionDO::inputsFlat.name}", 0))
        ).map { it.toModel() }.toList()
        saveToCache(nodes)

        return nodes
    }

    override suspend fun get(id: NodeId): Node? {
        return getCache(id)?.node ?: run {
            val node = MongoConnection.getCollection<NodeDO>(NODES_TABLE).find<NodeDO>(
                eq("id", id.str)
            ).map { it.toModel() }.firstOrNull()
            if (node != null) {
                saveToCache(node)
            }
            return node
        }
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