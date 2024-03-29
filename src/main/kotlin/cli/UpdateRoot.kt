package cli

import infra.ExpressionNetworkImpl
import model.SymbolId

suspend fun main() {
    val x = listOf(
        "low_min", "high_min", "money_min", " volume_min", "close_min", "gz2000_close_min", "cj_td_min",
        "open_min", "circulating_market_cap", "adj_factor", "close", "sector", "low", "high", "turnover_ratio",
        "vwap_min", "cd_lo_min", "cd_hi_min"
    ).map { SymbolId(it) }.toList()
    ExpressionNetworkImpl.forceUpdateRoot(x)
}