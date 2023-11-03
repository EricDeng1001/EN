package infra.db.mongo

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.File

object MongoConnection {
    var client : MongoClient
        private set

    init {
        val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
        val configYaml = File("mongo-config.yaml").readText()
        val configFile = yaml.decodeFromString<ConfigFile>(configYaml)
        println("configFile.dbUrl ${configFile.url}")
        client = MongoClient.create(configFile.url)
    }

    @Serializable
    class ConfigFile(val url: String) {

    }
}