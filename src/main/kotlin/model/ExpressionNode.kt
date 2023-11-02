package model

import kotlinx.serialization.Serializable

@Serializable

data class ExpressionNode(
    val effectivePtr: Pointer,
    val expectedPtr: Pointer,
    val expression: Expression
) : Node()