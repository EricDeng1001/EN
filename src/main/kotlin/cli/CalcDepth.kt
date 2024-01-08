package cli

import infra.ExpressionNetworkImpl

suspend fun main() {
    ExpressionNetworkImpl.calcDepthForAll()
}