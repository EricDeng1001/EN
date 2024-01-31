package infra.executor.ktorclient

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
data class Url(val run: String, val tryCancel: String, val deleteData: String)

@Serializable
data class ExecutorConfig(
    val http: Boolean,
    val host: String,
    val port: Int,
    val url: Url,
    val requestTimeoutMilliseconds: Long? = (60 * 1000),
    val connectTimeoutMilliseconds: Long? = (60 * 1000)
)

@Serializable
data class RunRequestBody(
    val expression: Expression, val start: Int, val end: Int, val taskId: TaskId, val priority: Int
)

@Serializable
data class RunResponseBody(val started: Boolean)

@Serializable
data class TryCancelResponseBody(val id: TaskId, val success: Boolean)

object HttpExecutor : Executor {

    private var config: ExecutorConfig

    init {
        val yaml = Yaml(
            configuration = YamlConfiguration(
                strictMode = false, yamlNamingStrategy = YamlNamingStrategy.KebabCase
            )
        )
        val configYaml = File("executor-config.yaml").readText()
        config = yaml.decodeFromString<ExecutorConfig>(configYaml)
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json(from = DefaultJson) { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMilliseconds
            connectTimeoutMillis = config.connectTimeoutMilliseconds
        }
    }

    override suspend fun run(task: Task): Boolean {
        val url = URLBuilder(
            protocol = if (config.http) URLProtocol.HTTP else URLProtocol.HTTPS,
            host = config.host,
            port = config.port,
        ).apply {
            path(config.url.run)
        }.toString()

        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(
                RunRequestBody(
                    expression = task.expression, start = task.from.value, end = task.to.value, taskId = task.id,
                    priority = task.priority
                )
            )
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("run failed: ${response.status} -> ${response.body<String>()}")
        }
        val body = response.body<RunResponseBody>()
        return body.started
    }

    override suspend fun tryCancel(id: TaskId) {
        val url = URLBuilder(
            protocol = if (config.http) URLProtocol.HTTP else URLProtocol.HTTPS,
            host = config.host,
            port = config.port,
        ).apply {
            path(config.url.tryCancel.replace("{id}", id))
        }.build().toString()

        val response = client.put(url)
        if (response.status != HttpStatusCode.OK) {
            throw Exception("tryCancel failed: ${response.status}")
        }
        val body = response.body<TryCancelResponseBody>()
    }

    override suspend fun deleteData(id: SymbolId) {
        val url = URLBuilder(
            protocol = if (config.http) URLProtocol.HTTP else URLProtocol.HTTPS,
            host = config.host,
            port = config.port,
        ).apply {
            path(config.url.deleteData.replace("{data_name}", id.str))
            parameters.append("confirm", "true")
        }.build().toString()

        val response = client.delete(url)
        if (response.status != HttpStatusCode.OK) {
            throw Exception("deleteData failed: ${response.status}")
        }
    }
}