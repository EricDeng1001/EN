package infra.db.mongo

import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.not
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

interface SymbolRepository {
    suspend fun delete(ids: List<SymbolId>)
    suspend fun findAllBesidesIds(ids: List<SymbolId>): List<SymbolId>
}

data class SymbolDO(
    @BsonId val id: ObjectId?,
    val name: String,
    val axis: String,
    val offset: String,
    val freq: String,
    val globalName: String? = null,
    val description: String? = null
)

object MongoSymbolRepository : SymbolRepository {

    private const val SYMBOLS_TABLE = "symbols"

    override suspend fun delete(ids: List<SymbolId>) {
        MongoConnection.getSymbolCollection<SymbolDO>(SYMBOLS_TABLE).deleteMany(
            not(
                `in`(SymbolDO::name.name, ids.map { it.str }.toList())
            )
        )
    }

    override suspend fun findAllBesidesIds(ids: List<SymbolId>): List<SymbolId> {
        return MongoConnection.getSymbolCollection<SymbolDO>(SYMBOLS_TABLE).find<SymbolDO>(
            not(
                `in`(SymbolDO::name.name, ids.map { it.str }.toList())
            )
        ).map { SymbolId(it.name) }.toList()
    }
}
