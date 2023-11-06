package infra.db.mongo

import model.*
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

data class NodeDO(
    @BsonId
    val id: ObjectId,
    val valid: Boolean,
    val effectivePtr: Int,
    val expectedPtr: Int,
    var isRunning: Boolean,
    var resetPtr: Boolean,
    val expression: ExpressionDO
) {
    data class ExpressionDO(
        val inputs: List<String>,
        val outputs: List<String>,
        val funcId: String,
        val shapeRule: ShapeRuleDO,
        val alignmentRule: AlignmentRuleDO,
        val arguments: Map<String, ArgumentDO>
    ) {
        data class ShapeRuleDO(val m: Int, val n: Int)
        data class AlignmentRuleDO(val offsets: Map<String, Int>)
        data class ArgumentDO(val value: String, val type: String)
    }
}

object MongoNodeTranslator {
    fun toMongo(shapeRule: Expression.ShapeRule): NodeDO.ExpressionDO.ShapeRuleDO {
        return NodeDO.ExpressionDO.ShapeRuleDO(shapeRule.m, shapeRule.n)
    }

    fun toMongo(alignmentRule: Expression.AlignmentRule): NodeDO.ExpressionDO.AlignmentRuleDO {
        return NodeDO.ExpressionDO.AlignmentRuleDO(
            alignmentRule.offsets.map { (k, v) -> k.str to v }.toMap()
        )
    }

    fun toMongo(argument: Argument): NodeDO.ExpressionDO.ArgumentDO {
        return NodeDO.ExpressionDO.ArgumentDO(argument.value, argument.type)
    }

    fun toMongo(expression: Expression): NodeDO.ExpressionDO {
        return NodeDO.ExpressionDO(
            expression.inputs.map { it.str },
            expression.outputs.map { it.str },
            expression.funcId.value,
            toMongo(expression.shapeRule),
            toMongo(expression.alignmentRule),
            expression.arguments.map { (k, v) -> k to toMongo(v) }.toMap()
        )
    }

    fun toMongo(node: Node, id: ObjectId): NodeDO {
        return NodeDO(
            id,
            node.valid,
            node.effectivePtr.value,
            node.expectedPtr.value,
            node.isRunning,
            node.resetPtr,
            toMongo(node.expression)
        )
    }

    fun toModel(shapeRule: NodeDO.ExpressionDO.ShapeRuleDO): Expression.ShapeRule {
        return Expression.ShapeRule(shapeRule.m, shapeRule.n)
    }

    fun toModel(alignmentRule: NodeDO.ExpressionDO.AlignmentRuleDO): Expression.AlignmentRule {
        return Expression.AlignmentRule(
            alignmentRule.offsets.map { (k, v) -> DataId(k) to v }.toMap()
        )
    }

    fun toModel(argument: NodeDO.ExpressionDO.ArgumentDO): Argument {
        return Argument(argument.value, argument.type)
    }


    fun toModel(expression: NodeDO.ExpressionDO): Expression {
        return Expression(
            expression.inputs.map { DataId(it) },
            expression.outputs.map { DataId(it) },
            FuncId(expression.funcId),
            toModel(expression.shapeRule),
            toModel(expression.alignmentRule),
            expression.arguments.map { (k, v) -> k to toModel(v) }.toMap()
        )
    }

    fun toModel(node: NodeDO): Node {
        return Node(
            node.valid,
            Pointer(node.effectivePtr),
            Pointer(node.expectedPtr),
            node.isRunning,
            node.resetPtr,
            toModel(node.expression)
        )
    }
}