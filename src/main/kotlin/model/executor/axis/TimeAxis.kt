package model.executor.axis

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// -------- Time Model -------- //
@Serializable
class TimeAxis {}

@Serializable
enum class Period(val value: Int) {
    D(4840), P(2420), M(20), T(1);

    companion object {
        fun parse(value: Int): Period {
            return when (value) {
                4840 -> D
                2420 -> P
                20 -> M
                1 -> T
                else -> throw Exception("invalid period tick format: $value")
            }
        }
    }
}

@Serializable
open class TimeBounds(open var start: Int, open var end: Int) : Iterable<Int> {

    override fun toString(): String {
        return "[$start-$end)"
    }

    override fun iterator(): Iterator<Int> {
        return object : Iterator<Int> {
            var current = start
            override fun hasNext(): Boolean {
                return current < end
            }

            override fun next(): Int {
                return current++
            }
        }

    }
}

@Serializable(with = TimeRange.TimeRangeSerializer::class)
class TimeRange(start: Int, end: Int, val period: Period = Period.D) : TimeBounds(
    start,
    end
) {
    class TimeRangeSerializer() : KSerializer<TimeRange> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TimeRange")
        override fun deserialize(decoder: Decoder): TimeRange {
            return fromString(decoder.decodeString())
        }

        override fun serialize(encoder: Encoder, value: TimeRange) {
            encoder.encodeString(value.toString())
        }
    }

    init {
        if ((this.end - this.start) % this.period.value != 0) {
            throw Exception("invalid time range param: $start, $end, $period")
        }
    }

    fun shift(offset: Int): TimeRange {
        return TimeRange(this.start + offset, this.end + offset, this.period)
    }

    fun offset(): Int {
        return this.start % this.period.value
    }

    fun limitStart(limit: Int) {
        val start = this.start + kotlin.math.ceil((limit - this.start) / this.period.value.toDouble())
            .toInt() * this.period.value
        if (start > this.start) {
            this.start = start
        }
    }

    fun limitEnd(limit: Int) {
        var last = this[-1]
        last -= kotlin.math.ceil((last - limit) / this.period.value.toDouble())
            .toInt() * this.period.value
        val end = last + this.period.value
        if (end < this.end) {
            this.end = end
        }
    }

    fun head(n: Int) {
        this.end = minOf(this.start + n * this.period.value, this.end)
    }

    fun tail(n: Int) {
        this.start = maxOf(this.end - n * this.period.value, this.start)
    }

    fun resizeHead(n: Int) {
        this.start = this.end - n * this.period.value
    }

    fun resizeTail(n: Int) {
        this.end = this.start + n * this.period.value
    }


    fun length(): Int {
        return (this.end - this.start) / this.period.value
    }

    operator fun get(index: Int): Int {
        var tick = this.start
        tick += if (index < 0) {
            val i = this.length() + index
            if (i < 0) {
                throw IndexOutOfBoundsException("index $index is out of bounds")
            }
            i * this.period.value
        } else {
            index * this.period.value
        }
        if (tick >= this.end) {
            throw IndexOutOfBoundsException("index $index is out of bounds")
        }
        return tick
    }

    companion object {
        /**
         * 将时间点规则化到时间偏移位置
         * @param timePoint: 时间点
         * @param offset: 时间偏移
         * @param period: 时间周期
         * @param direction: 方向，为true或为正时表示向后找一个最近的时间偏移点，为false或为负时表示向前找一个最近的时间偏移点
         * @param includeBound: 是否包含边界（当时间点正好落在时间偏移点上时，是否取当前值）
         * @return 规则化后的时间点
         */
        fun regularize(
            timePoint: Int, offset: Int, period: Period, direction: Boolean = true, includeBound: Boolean = false
        ): Int {
            if (offset >= period.value) {
                throw Exception("offset $offset is larger than period $period")
            }

            val directionV = if (direction) 1 else -1
            var d = timePoint / period.value
            val r = timePoint % period.value

            // 值为正，表示是当前周期，为负表示需要向direction方向移动一个周期
            var testValue = (offset - r) * directionV
            //不包含边界时，如果时间点正好落在时间偏移点上，需要向direction方向移动一个周期
            if (!includeBound && testValue == 0) {
                testValue = -1
            }
            if (testValue < 0) {
                d += directionV
            }

            return d * period.value + offset
        }

        fun makeRange(
            start: Int, end: Int, period: Period, offset: Int, includeStart: Boolean = true, includeEnd: Boolean = true
        ): TimeRange {
            val _start = this.regularize(start, offset, period, true, includeStart)
            var _end = this.regularize(end, offset, period, false, includeEnd)
            _end += period.value
            return TimeRange(_start, _end, period)

        }

        val regex = "^([\\[(])?([DPMTdpmt0-9]+)\\s?[-,]\\s?([DPMTdpmt0-9]+)([])])?(\\s?/\\s?([DPMTdpmt]))?\$".toRegex()
        fun fromString(str: String): TimeRange {
            val match = regex.find(str) ?: throw Exception("invalid time range format: $str")
            val (ls, start, end, rs, _, period) = match.destructured
            // todo: 解析是否包含边界
            return TimeRange(start.toInt(), end.toInt(), Period.valueOf(period))
        }
    }


    override fun iterator(): Iterator<Int> {
        return object : Iterator<Int> {
            var current = start
            override fun hasNext(): Boolean {
                return current < end
            }

            override fun next(): Int {
                val r = current
                current += period.value
                return r
            }
        }

    }


    override fun toString(): String {
        return "[$start-$end)/${period.name}"
    }

}

