package infra

import model.*

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DataInfoConfig(val http: Boolean, val host: String, val port: Int, val url: Url, val connectTimeoutMillis: Long,val requestTimeoutMillis: Long ) {
    @Serializable
    data class Url(
        val getExpressDataInfo: String,
    )
}


object HttpDataInfo : DataInfo {

    private val config = Yaml(
        configuration = YamlConfiguration(
            strictMode = false, yamlNamingStrategy = YamlNamingStrategy.KebabCase
        )
    ).decodeFromString<DataInfoConfig>(File("data-info-config.yaml").readText())

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json(from = DefaultJson) { ignoreUnknownKeys = true })
        }

        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeoutMillis
            requestTimeoutMillis = config.requestTimeoutMillis
        }
    }

    override suspend fun getExpressDataInfo(id: DataId, start: String?, end: String?, needPerf: String?): String {
        val url = URLBuilder(
            protocol = if (config.http) URLProtocol.HTTP else URLProtocol.HTTPS,
            host = config.host,
            port = config.port,
        ).apply {
            path(config.url.getExpressDataInfo.replace("{data_id}", id.str))
        }.toString()

        val response = client.get(url) {
            url {
                parameters.append("start", start.orEmpty())
                parameters.append("end", end.orEmpty())
                parameters.append("need_perf", needPerf.orEmpty())
            }
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("get data info from DataInfo failed: ${response.status} -> ${response.body<String>()}")
        }

        return response.body<String>()
    }

}