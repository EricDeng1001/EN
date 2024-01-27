package cli

import infra.ExpressionNetworkImpl
import kotlinx.coroutines.runBlocking
import model.SymbolId

fun main() {
    runBlocking {
        ExpressionNetworkImpl.forceRun(SymbolId(""))
    }
}