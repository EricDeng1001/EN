package model.executor.axis

// -------- Time Axis Mapping -------- //
class Mapping<T>(public val src: T, public val dst: T) {
    operator fun component1(): T {
        return src
    }

    operator fun component2(): T {
        return dst
    }
}

/**
 * 时间轴映射规则，源轴 -> 目标轴。
 * unit为一次迭代的最小单位，表示了最原始的数据映射关系，单位为tick，
 * block为一次计算的最小单位，单位为unit。
 *
 * @param shape: 单位（个），即为对应时间轴的几个周期
 * @param periods: 时间轴的周期
 * @param alignment: 对齐方式，0表示每个unit中的源的尾部和目标尾部是对齐的，为正数表示目标数据尾部向时间轴正方向移动alignment个单位
 */
class TimeAxisMapping(var shape: Mapping<Int>, var periods: Mapping<Period>, var alignment: Int, var block: Int = 1) {
    fun shapeM(): Int {
        return shape.src
    }

    fun shapeN(): Int {
        return shape.dst
    }

    fun unitM(): Int {
        return shapeM() * periods.src.value
    }

    fun unitN(): Int {
        return shapeN() * periods.dst.value
    }

    fun m(): Int {
        return unitM() + (block - 1) * shape.dst
    }

    fun n(): Int {
        return unitN() * block
    }

    fun setShapeM(value: Int) {
        this.shape = Mapping(value, shape.dst)
    }

    fun setShapeN(value: Int) {
        this.shape = Mapping(shape.src, value)
    }

    fun setUnitM(value: Int) {
        val r = value % periods.src.value
        if (r != 0) {
            throw Exception("invalid unitM: $value")
        }
        val unitM = value / periods.src.value
        this.setShapeM(unitM)
    }

    fun setUnitN(value: Int) {
        val r = value % periods.dst.value
        if (r != 0) {
            throw Exception("invalid unitN: $value")
        }
        val unitN = value / periods.dst.value
        this.setShapeN(unitN)
    }

    fun mn(): Mapping<Int> {
        return Mapping(m(), n())
    }

    fun mapping(timeRange: TimeRange) {
        TODO("Not yet implemented")
    }

    fun inverseMapping(time_range: TimeRange, srcOffset: Int = 0): TimeRange {
        val period = this.periods.src
        val (m, n) = this.mn()
        if (m == 0 || n == 0) {
            throw Exception("invalid mapping: $this")
        }
        var size = time_range.length() * time_range.period.value
        var end = time_range[-1]
        var start = time_range.start
        if (time_range.period.value >= period.value) {
            if (size < n) {
                throw Exception("output time range size $size is smaller than n $n")
            }
            // 窗口滚动
            end -= alignment
            start = end - size + n - m + period.value
        } else {
            // 升频
            var d = m / n
            val r = m % n
            if (r != 0) {
                throw Exception("shape $shape is not valid, $period, ${time_range.period}")
            }

            // 没有落在offset上
            if (end % period.value != srcOffset) {
                var dd = end / period.value
                val rr = end % period.value
                if (rr >= srcOffset) {
                    dd += 1
                }
                end = dd * period.value + srcOffset
            }
            val tickLen = maxOf(end - time_range.start + 1, 0)
            var inputLen = tickLen / period.value
            val rr = tickLen % period.value

            if (rr != 0) {
                inputLen += 1
            }
            if (inputLen == 0) {
                inputLen = 1
            }
            size = inputLen * period.value
            end -= alignment
            start = end - size + n - m + period.value
        }
        return TimeRange(start, end + period.value, period)
    }


    override fun toString(): String {
        return "(${this.m()}:${this.n()}/${this.block}, ${this.periods.src.name}->${this.periods.dst.name}, ${this.alignment})"
    }

    companion object {
        private val regex = "\\(?(\\d+):(\\d+)(/(\\d+))?,\\s*(\\w+)->(\\w+),\\s*(\\d+)\\)?".toRegex()

        /**
         * 从字符串中解析出TimeAxisMapping
         * @param str: format: (4840:4840/1, M->M, 0)
         */
        fun fromStr(str: String): TimeAxisMapping {
            regex.find(str)?.let {
                val (sm, sn, _, sblock, speriodM, speriodN, salignment) = it.destructured
                val m = sm.toInt()
                val n = sn.toInt()
                val block = if (sblock == "") 1 else sblock.toInt()
                val unitM = m - (block - 1) * n / block
                val unitN = n / block
                val periods = Mapping(Period.valueOf(speriodM), Period.valueOf(speriodN))
                val shape = Mapping(unitM / periods.src.value, unitN / periods.dst.value)
                val alignment = salignment.toInt()

                return TimeAxisMapping(shape, periods, alignment, block)
            } ?: throw Exception("TimeAxisMapping str format error: $str")
        }
    }
}


// -------- Time Axis Mapping Rule -------- //
abstract class TimeAxisMappingRule {
    abstract fun handle(timeRange: TimeRange): TimeRange
}

abstract class ParameterMappingRule{
    abstract fun handle(mapping: TimeAxisMapping)
}