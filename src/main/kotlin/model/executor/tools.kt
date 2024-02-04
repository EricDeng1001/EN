package model.executor

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import model.executor.axis.TimeRange
import java.util.concurrent.atomic.AtomicBoolean


class AtomicBooleanSerializer : KSerializer<AtomicBoolean> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AtomicBoolean")
    override fun deserialize(decoder: Decoder): AtomicBoolean {
        return AtomicBoolean(decoder.decodeString().toBoolean())
    }

    override fun serialize(encoder: Encoder, value: AtomicBoolean) {
        encoder.encodeString(value.toString())
    }
}