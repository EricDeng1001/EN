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
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class WorkerConfig(val http: Boolean, val host: String, val port: Int, val url: Url) {
    @Serializable
    data class Url(
        val getExpressDataInfo: String,
    )
}


object HttpWorker : Worker {

    private val config = Yaml(
        configuration = YamlConfiguration(
            strictMode = false, yamlNamingStrategy = YamlNamingStrategy.KebabCase
        )
    ).decodeFromString<WorkerConfig>(File("worker-config.yaml").readText())

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json(from = DefaultJson) { ignoreUnknownKeys = true })
        }
    }

    override suspend fun getExpressDataInfo(id: DataId, start: String?, end: String?): String {
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
            }
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("get data info from worker failed: ${response.status} -> ${response.body<String>()}")
        }

        return response.body<String>()
    }

}