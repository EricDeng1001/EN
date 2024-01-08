package cli

import infra.ExpressionNetworkImpl
import kotlinx.coroutines.runBlocking
import model.DataId

fun main() {
    runBlocking {
        ExpressionNetworkImpl.forceRun(DataId(""))
    }
}