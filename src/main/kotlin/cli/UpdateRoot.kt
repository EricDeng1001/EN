package cli

import infra.ExpressionNetworkImpl
import kotlinx.coroutines.delay
import model.DataId

suspend fun main() {
    val x = listOf(
        "low_min", "high_min", "money_min", " volume_min", "close_min", "gz2000_close_min", "cj_td_min",
        "open_min", "circulating_market_cap", "adj_factor", "close", "sector", "low", "high", "turnover_ratio",
        "vwap_min", "cd_lo_min", "cd_hi_min"
    ).map { DataId(it) }.toList()
    ExpressionNetworkImpl.forceUpdateRoot(x)

    delay(3600 * 1000)
}