package infra.db.mongo

import com.mongodb.client.model.Projections
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

interface NotebookRepository {

    suspend fun queryAllNoteBook(): List<Notebook>
}

@JvmInline
@Serializable
value class SymbolId(val str: String)

@Serializable
@JvmInline
value class NotebookName(val str: String)

@Serializable
data class Notebook(
    val name: NotebookName,
    var container: MutableList<SymbolId> = mutableListOf(),
)

data class NotebookDO(
    @BsonId val id: ObjectId?,
    val name: String,
    val container: List<String>,
) {
    fun toModel(): Notebook {
        return Notebook(
            NotebookName(name),
            container.map { SymbolId(it) }.toMutableList(),
        )
    }
}


object MongoNotebookRepository : NotebookRepository {

    private const val NOTEBOOKS_TABLE = "notebooks"

    override suspend fun queryAllNoteBook(): List<Notebook> {
        return MongoConnection.getNotebooksCollection<NotebookDO>(NOTEBOOKS_TABLE).find<NotebookDO>().projection(
            Projections.include(
                listOf(
                    NotebookDO::name.name,
                    NotebookDO::container.name,
                )
            )
        ).map { it.toModel() }.toList()
    }
}
