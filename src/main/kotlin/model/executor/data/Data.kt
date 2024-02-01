package model.executor.data

import kotlinx.serialization.Serializable
import model.InputType
import model.executor.axis.Period


@Serializable
class Data(val meta: DataMeta) {
}

@Serializable
data class InputUnit(val type: InputType, val datas: List<Data>) {

    constructor(data: Data) : this(InputType.DataId, listOf(data))

    constructor(datas: List<Data>) : this(InputType.List, datas)

    fun isList(): Boolean = type == InputType.List

    fun data(): Data? = if (datas.isEmpty()) null else datas[0]

    fun list(): List<Data> = datas
}

typealias Inputs = List<InputUnit>
typealias Outputs = List<Data>

@Serializable
data class DataMeta(val name: String, val axis: String, val period: Period, val offset: Int) {
}
