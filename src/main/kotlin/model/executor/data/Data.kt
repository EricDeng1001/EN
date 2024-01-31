package model.executor.data

import model.executor.axis.Period


class Data (meta: DataMeta){

}

data class DataMeta(val name: String, val axis: String, val period: Period, val offset: Int) {
}
