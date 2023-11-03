package model

import kotlinx.serialization.Serializable

@Serializable
data class Node(
    val effectivePtr: Pointer,
    val expectedPtr: Pointer,
    val expression: Expression
)