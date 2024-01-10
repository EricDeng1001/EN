package infra

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
import model.Symbol
import model.SymbolId
import model.SymbolLibraryService
import java.io.File

@Serializable
data class SymbolLibraryConfig(val http: Boolean, val host: String, val port: Int, val url: Url) {
    @Serializable
    data class Url(
        val getSymbol: String,
    )
}


object HttpSymbolLibrary : SymbolLibraryService {

    private val config = Yaml(
        configuration = YamlConfiguration(
            strictMode = false, yamlNamingStrategy = YamlNamingStrategy.KebabCase
        )
    ).decodeFromString<SymbolLibraryConfig>(File("symlib-config.yaml").readText())

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json(from = DefaultJson) { ignoreUnknownKeys = true })
        }
    }

    override suspend fun getSymbol(symbolId: SymbolId): Symbol {
        val url = URLBuilder(
            protocol = if (config.http) URLProtocol.HTTP else URLProtocol.HTTPS,
            host = config.host,
            port = config.port,
        ).apply {
            path(config.url.getSymbol)
        }.toString()

        val response = client.get(url) {
            url {
                parameters.append("symbol_id", symbolId.str)
            }
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("get symbol failed: ${response.status} -> ${response.body<String>()}")
        }

        return response.body<Symbol>()
    }

}