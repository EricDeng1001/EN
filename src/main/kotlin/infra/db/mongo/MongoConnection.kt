package infra.db.mongo

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.File

object MongoConnection {
    var client: MongoClient
        private set

    var defaultDatabase: MongoDatabase
        private set

    init {
        val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
        val configYaml = File("mongo-config.yaml").readText()
        val configFile = yaml.decodeFromString<ConfigFile>(configYaml)
        client = MongoClient.create(configFile.url)
        defaultDatabase = client.getDatabase(configFile.defaultDatabase)
        println("configFile.dbUrl ${configFile.url}, defaultDatabase ${configFile.defaultDatabase}")
    }

    @Serializable
    class ConfigFile(val url: String, val defaultDatabase: String) {

    }
}