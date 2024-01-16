package infra.db.mongo

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.concurrent.TimeUnit

object MongoConnection {

    var config: ConfigFile
        private set

    var client: MongoClient
        private set


    init {
        val yaml = Yaml(
            configuration = YamlConfiguration(
                strictMode = false, yamlNamingStrategy = YamlNamingStrategy.KebabCase
            )
        )
        val configYaml = File("mongo-config.yaml").readText()
        config = yaml.decodeFromString<ConfigFile>(configYaml)
        client = MongoClient.create(
            MongoClientSettings.builder().applyConnectionString(ConnectionString(config.url))
                .applyToConnectionPoolSettings { builder ->
                    builder.maxWaitTime(config.maxConnectionWaitTimeSeconds, TimeUnit.SECONDS)
                        .maxSize(config.maxConnectionPoolSize)
                }.build()
        )
        println("configFile.dbUrl ${config.url}, defaultDatabase ${config.defaultDatabase}")
    }

    inline fun <reified T : Any> getCollection(collectionName: String, database: String? = null): MongoCollection<T> {
        if (database.isNullOrBlank()) {
            return client.getDatabase(config.defaultDatabase).getCollection<T>(collectionName)
        }
        return client.getDatabase(database).getCollection<T>(collectionName)
    }
    inline fun <reified T : Any> getNotebooksCollection(collectionName: String, database: String? = null): MongoCollection<T> {
        if (database.isNullOrBlank()) {
            return client.getDatabase(config.notebooksDatabase).getCollection<T>(collectionName)
        }
        return client.getDatabase(database).getCollection<T>(collectionName)
    }

    inline fun <reified T : Any> getSymbolCollection(collectionName: String, database: String? = null): MongoCollection<T> {
        if (database.isNullOrBlank()) {
            return client.getDatabase(config.symbolDatabase).getCollection<T>(collectionName)
        }
        return client.getDatabase(database).getCollection<T>(collectionName)
    }

    @Serializable
    class ConfigFile(
        val url: String,
        val defaultDatabase: String,
        val notebooksDatabase: String,
        val symbolDatabase: String,
        val maxConnectionWaitTimeSeconds: Long,
        val maxConnectionPoolSize: Int
    )
}