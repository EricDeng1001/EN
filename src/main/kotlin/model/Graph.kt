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
data class Graph(val expressions: List<Expression>) {

    private val graphView: GraphView

    init {
        val nodes: MutableMap<String, GraphView.GraphNode> = HashMap()
        val edges: MutableList<GraphView.GraphEdge> = ArrayList()
        for (ex in expressions) {
            for (input in ex.inputs) {
                for (i in input.ids){
                    nodes.computeIfAbsent(i.str) {
                        GraphView.GraphNode(
                            GraphView.GraphNode.Id(i.str), "data", i.str
                        )
                    }
                }
            }
            for (output in ex.outputs) {
                nodes.computeIfAbsent(output.str) {
                    GraphView.GraphNode(
                        GraphView.GraphNode.Id(output.str), "data", output.str
                    )
                }
            }

            val opId = "${ex.funcId.value}_${ex.outputs[0]}"
            nodes[opId] =
                GraphView.GraphNode(
                    GraphView.GraphNode.Id(opId), "operator", ex.funcId.value
                )
        }

        for (ex in expressions) {
            val opId = "${ex.funcId.value}_${ex.outputs[0]}"
            val operator = nodes[opId]!!
            for (input in ex.inputs) {
                for(i in input.ids){
                    edges.add(GraphView.GraphEdge(nodes[i.str]!!.id, operator.id))
                }
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

}