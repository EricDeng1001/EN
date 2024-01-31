package model

import kotlinx.serialization.Serializable
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@Serializable
data class GraphView(val nodes: List<GraphNode>, val edges: List<GraphEdge>) {

    @Serializable
    data class GraphNode(val id: Id, val type: String, val parseId: String) {

        @JvmInline
        @Serializable
        value class Id(val str: String)
    }

    @Serializable
    data class GraphEdge(val source: GraphNode.Id, val target: GraphNode.Id)
}

@Serializable
data class GraphDebugView(val nodes: List<GraphDebugNode>, val edges: List<GraphDebugEdge>) {

    @Serializable
    data class GraphDebugNode(val id: Id, val type: String, val parseId: String, val debugInfo: DebugInfo? = null) {

        @JvmInline
        @Serializable
        value class Id(val str: String)

        @Serializable
        data class DebugInfo(
            val valid: Boolean,
            val effectivePtr: Int,
            val expectedPtr: Int,
            val isPerfCalculated: Boolean,
            val mustCalculate: Boolean,
            val shouldUpdate: Boolean,
            val expression: Expression
        )
    }

    @Serializable
    data class GraphDebugEdge(val source: GraphDebugNode.Id, val target: GraphDebugNode.Id)
}


@Serializable
data class Graph(val nodes: List<Node>, val inputs: List<Node>) {

    private val nodesMap: MutableMap<DataId, DataId> = HashMap()

    private val edges: MutableList<Pair<DataId, DataId>> = ArrayList()

    private val opsMap: MutableMap<DataId, String> = HashMap()

    init {
        for (node in nodes) {
            val ex = node.expression

            if (ex.isRoot()) {
                continue
            }

            for (input in ex.inputs) {
                for (i in input.ids) {
                    nodesMap.computeIfAbsent(i) { i }
                }
            }
            for (output in ex.outputs) {
                nodesMap.computeIfAbsent(output) { output }
            }

            val opId = DataId("${ex.funcId.value}_${ex.outputs[0]}")
            opsMap[opId] = ex.funcId.value
        }

        for (node in nodes) {
            val ex = node.expression

            if (ex.isRoot()) {
                continue
            }

            val opId = DataId("${ex.funcId.value}_${ex.outputs[0]}")
            for (input in ex.inputs) {
                for (i in input.ids) {
                    edges.add(Pair(i, opId))
                }
            }
            for (output in ex.outputs) {
                edges.add(Pair(opId, output))
            }
        }
    }

    fun view(): GraphView {
        val ns = nodesMap.values.map {
            GraphView.GraphNode(GraphView.GraphNode.Id(it.str), "data", it.str)
        }.toList()

        val os = opsMap.map { (k, v) ->
            GraphView.GraphNode(GraphView.GraphNode.Id(k.str), "operator", v)
        }.toList()

        val es = edges.map { (k, v) ->
            GraphView.GraphEdge(GraphView.GraphNode.Id(k.str), GraphView.GraphNode.Id(v.str))
        }.toList()

        return GraphView(ns + os, es)
    }

    fun debugView(): GraphDebugView {
        val cache = nodes.associateBy { it.id.str }.toMap() + inputs.associateBy { it.id.str }.toMap()
        val ns = nodesMap.values.map {
            GraphDebugView.GraphDebugNode(
                GraphDebugView.GraphDebugNode.Id(it.str),
                "data",
                it.str,
                GraphDebugView.GraphDebugNode.DebugInfo(
                    cache[it.str]!!.valid,
                    cache[it.str]!!.effectivePtr.value,
                    cache[it.str]!!.expectedPtr.value,
                    cache[it.str]!!.isPerfCalculated,
                    cache[it.str]!!.mustCalculate,
                    cache[it.str]!!.shouldUpdate,
                    cache[it.str]!!.expression
                )
            )
        }.toList()

        val os = opsMap.map { (k, v) ->
            GraphDebugView.GraphDebugNode(
                GraphDebugView.GraphDebugNode.Id(k.str), "operator", v, null
            )
        }.toList()

        val es = edges.map { (k, v) ->
            GraphDebugView.GraphDebugEdge(
                GraphDebugView.GraphDebugNode.Id(k.str), GraphDebugView.GraphDebugNode.Id(v.str)
            )
        }.toList()

        return GraphDebugView(ns + os, es)
    }
}

@Serializable
data class UpdateGraph(val nodes: List<Node>) {

    fun debugView(): GraphDebugView {
        val ns = nodes.map {
            GraphDebugView.GraphDebugNode(
                GraphDebugView.GraphDebugNode.Id(it.id.str),
                "data",
                it.id.str,
                GraphDebugView.GraphDebugNode.DebugInfo(
                    it.valid,
                    it.effectivePtr.value,
                    it.expectedPtr.value,
                    it.isPerfCalculated,
                    it.mustCalculate,
                    it.shouldUpdate,
                    it.expression
                )
            )
        }.toList()

        val es = HashMap<DataId, DataId>()

        for (node in nodes) {
            val ex = node.expression

            if (ex.isRoot()) {
                continue
            }

            for (n in nodes) {
                for (input in n.expression.inputs) {
                    for (i in input.ids) {
                        for (output in n.expression.outputs) {
                            es[i] = output
                        }
                    }
                }
            }
        }

        return GraphDebugView(ns, es.map { (k, v) ->
            GraphDebugView.GraphDebugEdge(
                GraphDebugView.GraphDebugNode.Id(k.str), GraphDebugView
                    .GraphDebugNode.Id(v.str)
            )
        })
    }
}