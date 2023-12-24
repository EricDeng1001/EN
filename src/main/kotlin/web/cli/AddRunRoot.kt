package web.cli

import infra.ExpressionNetworkImpl
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>): Unit {
    runBlocking {
        ExpressionNetworkImpl.updateRunRootInfo()
    }
}