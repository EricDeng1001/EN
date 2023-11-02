package model

import kotlinx.serialization.Serializable

@Serializable
data class Argument(val value: String, val type: String) {

}
