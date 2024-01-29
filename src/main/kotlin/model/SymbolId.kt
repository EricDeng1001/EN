package model

import kotlinx.serialization.Serializable


@JvmInline
@Serializable
value class SymbolId(val str: String)
