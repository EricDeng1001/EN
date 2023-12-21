package infra

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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.Horizon
import model.HorizonConfig
import model.User
import java.io.File

object HttpHorizonService : Horizon {

    private val config = Yaml(
        configuration = YamlConfiguration(
            strictMode = false, yamlNamingStrategy = YamlNamingStrategy.KebabCase
        )
    ).decodeFromString<HorizonConfig>(File("horizon-config.yaml").readText())

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json(from = DefaultJson) { ignoreUnknownKeys = true })
        }
    }



    /**
     * """
     *         {'id': 10000008,
     *         'create_at': '2022-09-14T15:36:48Z',
     *         'update_at': '2023-06-20T05:11:23.878Z',
     *         'username': 'qiaobaochen',
     *         'nickname': '乔宝琛',
     *         'password': '',
     *         'status': 'active',
     *         'email': 'qiaobaochen@techfin.ai',
     *         'phone': '',
     *         'roles': ['computing-admin'],
     *         'groups': ['computing', 'trade', 'strategy', 'model']}
     *"""
     */

    override suspend fun queryUserInfoByToken(token: String): User? {
        val url = URLBuilder(
            protocol = if (config.http) URLProtocol.HTTP else URLProtocol.HTTPS,
            host = config.host,
            port = config.port,
        ).apply {
            path(config.url.me)
        }.toString()

        val response = client.get(url) {
            header("Authorization", token)
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            return null
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception(response.body<String>())
        }

        return try {
            response.body<User>()
        } catch (e: NoTransformationFoundException) {
            null
        }
    }

}