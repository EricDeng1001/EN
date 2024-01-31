package model.executor.axis

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class TimeRangeTest {

    @Test
    operator fun iterator() {

        val tr = TimeRange(0, Period.D.value * 11, Period.D)
        println(tr)
        for (i in tr) {
            println(i)
        }
    }

}