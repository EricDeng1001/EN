package infra.datamanager.ktorclient

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.DataId
import model.DataManager
import model.Pointer
import java.io.File

@Serializable
data class Url(val findLastPtr: String)

@Serializable
data class DataManagerConfig(val http: Boolean, val host: String, val port: Int, val url: Url)
object HttpDataManager : DataManager {

    private var config: DataManagerConfig

    init {
        val yaml = Yaml(
            configuration = YamlConfiguration(
                strictMode = false, yamlNamingStrategy = YamlNamingStrategy.KebabCase
            )
        )
        val configYaml = File("datamanager-config.yaml").readText()
        config = yaml.decodeFromString<DataManagerConfig>(configYaml)
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json(from = DefaultJson) { ignoreUnknownKeys = true })
        }
    }

    override suspend fun findLastPtr(id: DataId): Pointer {
        val url = URLBuilder(
            protocol = if (config.http) URLProtocol.HTTP else URLProtocol.HTTPS,
            host = config.host,
            port = config.port,
        ).apply {
            path(config.url.findLastPtr.replace("{id}", id.str))
        }.toString()

        val response = client.get(url)
        if (response.status != HttpStatusCode.OK) {
            throw Exception("findLastPtr failed: ${response.status}")
        }
        return Pointer(response.body<Int>())
    }
}