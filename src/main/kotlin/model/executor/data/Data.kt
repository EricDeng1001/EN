package model.executor.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import model.InputType
import model.executor.axis.Period
import model.executor.axis.TimeAxisMapping
import model.executor.axis.TimeBounds
import model.executor.axis.TimeRange


@Serializable
class Data(val meta: DataMeta) {
}

@Serializable
class DataRef(val name:String, val timeBounds: TimeBounds)

@Serializable
open class TimeBaseInput<T>(val type: InputType, val datas: List<T>) {

    var timeAxisMapping: TimeAxisMapping? = null

    constructor(data: T) : this(InputType.DataId, listOf(data))

    constructor(datas: List<T>) : this(InputType.List, datas)

    fun isList(): Boolean = type == InputType.List

    fun data(): T? = if (datas.isEmpty()) null else datas[0]

    fun list(): List<T> = datas
}

typealias InputsItem = TimeBaseInput<Data>
typealias Inputs = List<InputsItem>
typealias Outputs = List<Data>

typealias InputsRef = List<TimeBaseInput<DataRef>>

@Serializable
data class DataMeta(val name: String, val axis: String, val period: Period, val offset: Int) {
}
