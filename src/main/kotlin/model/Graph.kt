package model

import kotlinx.serialization.Serializable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@Serializable
data class GraphView(val nodes: List<GraphNode>, val edges: List<GraphEdge>) {

    @Serializable
    data class GraphNode(val id: Id, val type: String, val name: String?) {

        @JvmInline
        @Serializable
        value class Id(val str: String)
    }

    @Serializable
    data class GraphEdge(val from: GraphNode.Id, val to: GraphNode.Id)
}

@Serializable
data class Graph(val expressions: List<Expression>) {

    private val graphView: GraphView

    init {
        val nodes: MutableMap<String, GraphView.GraphNode> = HashMap()
        val edges: MutableList<GraphView.GraphEdge> = ArrayList()
        for (ex in expressions) {
            for (input in ex.inputs) {
                nodes.computeIfAbsent(input.str) {
                    GraphView.GraphNode(
                        GraphView.GraphNode.Id(genId()), "data", input
                            .str
                    )
                }
            }
            for (output in ex.outputs) {
                nodes.computeIfAbsent(output.str) {
                    GraphView.GraphNode(
                        GraphView.GraphNode.Id(genId()), "data", output.str
                    )
                }
            }

            nodes.computeIfAbsent(ex.funcId.value) {
                GraphView.GraphNode(
                    GraphView.GraphNode.Id(genId()), "operator", ex.funcId.value
                )
            }
        }

        for (ex in expressions) {
            val operator = nodes[ex.funcId.value]!!
            for (input in ex.inputs) {
                edges.add(GraphView.GraphEdge(nodes[input.str]!!.id, operator.id))
            }
            for (output in ex.outputs) {
                edges.add(GraphView.GraphEdge(operator.id, nodes[output.str]!!.id))
            }
        }
        graphView = GraphView(nodes.values.toList(), edges)
    }

    fun view(): GraphView {
        return graphView
    }

    private fun genId() = UUID.randomUUID().toString().replace("-", "")
}