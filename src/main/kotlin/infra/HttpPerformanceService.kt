package infra

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*

import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.*
import java.io.File

@Serializable
data class Url(val calculate: String)

@Serializable
data class PerformanceConfig(val http: Boolean, val host: String, val port: Int, val url: Url)

object HttpPerformanceService : PerformanceService {

    private var config: PerformanceConfig

    init {
        val yaml = Yaml(
            configuration = YamlConfiguration(
                strictMode = false, yamlNamingStrategy = YamlNamingStrategy.KebabCase
            )
        )
        val configYaml = File("performance-config.yaml").readText()
        config = yaml.decodeFromString<PerformanceConfig>(configYaml)
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json(from = DefaultJson) { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        }
    }

    override suspend fun calculate(id: SymbolId) {
        val url = URLBuilder(
            protocol = if (config.http) URLProtocol.HTTP else URLProtocol.HTTPS,
            host = config.host,
            port = config.port,
        ).apply {
            path(config.url.calculate.replace("{id}", id.str))
        }.toString()

        val response = client.post(url)

        if (response.status != HttpStatusCode.OK) {
            throw Exception("run failed: ${response.status} -> ${response.body<String>()}")
        }
    }
}