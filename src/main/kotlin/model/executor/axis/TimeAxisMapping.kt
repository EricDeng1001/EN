package model.executor.axis

import kotlinx.serialization.Serializable
import model.ArgName
import model.Argument
import model.executor.data.Data
import model.executor.data.Inputs
import model.executor.data.Outputs

// -------- Time Axis Mapping -------- //
@Serializable
class Mapping<T>(val src: T, val dst: T) {
    operator fun component1(): T {
        return src
    }

    operator fun component2(): T {
        return dst
    }
}


class SplitTimeRangeError(message: String = "split time range error") : Exception(message)

/**
 * 时间轴映射规则，源轴 -> 目标轴。
 * unit为一次迭代的最小单位，表示了最原始的数据映射关系，单位为tick，
 * block为一次计算的最小单位，单位为unit。
 *
 * @param shape: 单位（个），即为对应时间轴的几个周期
 * @param periods: 时间轴的周期
 * @param alignment: 对齐方式，0表示每个unit中的源的尾部和目标尾部是对齐的，为正数表示目标数据尾部向时间轴正方向移动alignment个单位
 */
@Serializable
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

    fun splitDstTimeRange(timeRange: TimeRange) {
        if (timeRange.length() == 0) {
            throw TimeRangeZeroLengthError()
        }
        if (timeRange.period != this.periods.dst) {
            throw SplitTimeRangeError(
                "time_range($timeRange) period must be equal to dst periods: ${
                    this.periods.dst.name
                }"
            )
        }
        // todo
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
        fun fromString(str: String): TimeAxisMapping {
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
    abstract fun handle(mapping: TimeAxisMapping, datas: Mapping<Data>, arguments: Map<ArgName, Argument>)
}

abstract class ParameterMappingRule(val name: String) : TimeAxisMappingRule() {

    fun getNamedValue(arguments: Map<ArgName, Argument>): Argument? {
        return arguments[name]
    }

}

// -------- 具体的映射规则 -------- //

class AlignmentMappingRule : TimeAxisMappingRule() {
    override fun handle(mapping: TimeAxisMapping, datas: Mapping<Data>, arguments: Map<ArgName, Argument>) {
        if (datas.src.meta.period == datas.dst.meta.period) {
            val srcOffset = datas.src.meta.offset
            val dstOffset = datas.dst.meta.offset
            if (srcOffset > dstOffset) {
                mapping.alignment = datas.src.meta.period.value
            }
        }
    }
}

class UpSamplingMethodMappingRule : ParameterMappingRule("method") {
    override fun handle(mapping: TimeAxisMapping, datas: Mapping<Data>, arguments: Map<ArgName, Argument>) {
        if (datas.src.meta.period > datas.dst.meta.period) {
            mapping.setUnitN(datas.src.meta.period.value)
        }
        val value = this.getNamedValue(arguments) ?: throw Exception("method is not set")
        if ("forward" == value.value) {
            mapping.alignment = datas.src.meta.period.value
        }
    }
}

class WindowsSizeMappingRule : ParameterMappingRule("window_size") {
    override fun handle(mapping: TimeAxisMapping, datas: Mapping<Data>, arguments: Map<ArgName, Argument>) {
        val value = this.getNamedValue(arguments)?.value?.toInt() ?: 1
        mapping.setShapeM(value)
    }
}

class StepMappingRule : ParameterMappingRule("step") {
    override fun handle(mapping: TimeAxisMapping, datas: Mapping<Data>, arguments: Map<ArgName, Argument>) {
        val value = this.getNamedValue(arguments)?.value?.toInt() ?: 1
        mapping.setUnitN(value * mapping.periods.src.value)
    }
}

class ReduceMappingRule : ParameterMappingRule("reduce") {
    override fun handle(mapping: TimeAxisMapping, datas: Mapping<Data>, arguments: Map<ArgName, Argument>) {
        val value = this.getNamedValue(arguments)?.value.toBoolean() ?: return // todo: boolean string to boolean method
        if (!value) {
            if (datas.src.meta.period != datas.dst.meta.period) {
                throw Exception("input period must be equal to output period, when reduce is false")
            }
            val step = this.getNamedValue(arguments)?.value?.toInt() ?: 1
            val windowSize = this.getNamedValue(arguments)?.value?.toInt() ?: 1
            if (step != windowSize) {
                throw Exception("step must be equal to window_size, when reduce is false")
            }
        }

    }
}


// -------- Time Axis Mapping Rule Table -------- //
class TimeAxisMappingRuleTable {

    companion object {
        private var ruleChain = listOf(
            AlignmentMappingRule(),
            WindowsSizeMappingRule(),
            StepMappingRule(),
            UpSamplingMethodMappingRule(),
            ReduceMappingRule(),
        )

        fun generateMapping(
            periods: Mapping<Period>,
            datas: Mapping<Data>,
            arguments: Map<ArgName, Argument>
        ): TimeAxisMapping {
            val mapping = TimeAxisMapping(shape = Mapping(1, 1), periods = periods, alignment = 0)
            for (rule in ruleChain) {
                rule.handle(mapping, datas, arguments)
            }
            return mapping
        }

        fun generateMappings(inputs: Inputs, outputs: Outputs, arguments: Map<ArgName, Argument>) {
            val output = outputs.first()
            for (data in inputs) {
                val d = data.data() ?: continue
                val mapping = generateMapping(Mapping(d.meta.period, output.meta.period), Mapping(d, output), arguments)
                data.timeAxisMapping = mapping
            }
        }

    }

}
