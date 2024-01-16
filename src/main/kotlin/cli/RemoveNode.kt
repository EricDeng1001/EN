package cli

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import infra.ExpressionNetworkImpl
import infra.db.mongo.MongoNodeRepository
import infra.db.mongo.MongoNotebookRepository
import infra.db.mongo.MongoSymbolRepository
import infra.db.mongo.SymbolId
import kotlinx.coroutines.runBlocking
import model.DataId
import model.NodeId

fun main() {
    val containerOutput = "all_container_id.csv"
    val nodeIdInput = "eig_id.csv"
    val nodeIdOutput = "all_out_id.csv"
    val nodeIdErrMsg = "err_msg.csv"

    runBlocking {
        //1. 获取notebooks containerIds
        getAllNodesIdByNotebooks(containerOutput)

        //2. 找到需要保留的节点的上游节点
        getAllNodesIdByCSV(nodeIdInput, nodeIdOutput, nodeIdErrMsg)

        //3. 去重，存入res
        val resIds = HashSet<String>()
        getNodeIdFromCSV(nodeIdOutput).forEach {
            resIds.add(it)
        }
        getNodeIdFromCSV(containerOutput).forEach {
            resIds.add(it)
        }
        saveNodeIdToCSV("res.csv", resIds)

        //4. 找出除了res以外的node ids(线上删除tfdb数据)
        getNodeIdsNotIn()

        //5. 删除en
        deleteEN()

        //6. 删除symbol service
        deleteSymbol()
    }
}

fun saveNodeIdToCSV(filePath: String, idSet: HashSet<String>) {
    val rows = idSet.map { listOf(it) }
    csvWriter().writeAll(rows, filePath)
}

fun getNodeIdFromCSV(filePath: String): List<String> {
    val nodeIds: MutableList<String> = ArrayList()
    csvReader().open(filePath) {
        readAllAsSequence().forEach{
            nodeIds.add(it[0])
        }
    }
    return nodeIds
}

fun getNodeIdFromOriginCSV(filePath: String): List<String> {
    val nodeIds: MutableList<String> = ArrayList()
    csvReader().open(filePath) {
        readAllAsSequence().forEachIndexed { index, strings ->
            if (index != 0) {
                nodeIds.add(strings[1])
            }
        }
    }
    return nodeIds
}

suspend fun getUpStreamNodes(nodeId: String): List<String> {
    return ExpressionNetworkImpl.findAllUpstreamNode(DataId(nodeId))
}

suspend fun getAllNodesIdByCSV(input: String, output: String, errOutput: String) {
    val nodeIds = getNodeIdFromOriginCSV(input)
    val allIdSet = HashSet<String>()
    val errSet = HashSet<String>()
    nodeIds.forEach { nodeId ->
        try {
            println("processing $nodeId")
            getUpStreamNodes(nodeId).forEach {
                allIdSet.add(it)
            }
        } catch (e: Exception) {
            errSet.add(e.toString())
        }
    }
    println("errSet.size: ${errSet.size}")
    println("allIdSet.size: ${allIdSet.size}")
    saveNodeIdToCSV(errOutput, errSet)
    saveNodeIdToCSV(output, allIdSet)
}

suspend fun getAllNotebooksContainer(): HashSet<String> {
    val containerSet = HashSet<String>()
    val notebooks = MongoNotebookRepository.queryAllNoteBook()
    println(notebooks.size)
    notebooks.forEach { notebook ->
        notebook.container.forEach {
            containerSet.add(it.str)
        }
    }
    return containerSet
}

suspend fun getAllNodesIdByNotebooks(fileOutputPath: String) {
    val allContainerIdSet = getAllNotebooksContainer()

    println("allContainerIdSet.size: ${allContainerIdSet.size}")
    saveNodeIdToCSV(fileOutputPath, allContainerIdSet)
}

suspend fun getNodeIdsNotIn(){
    val res = getNodeIdFromCSV("res.csv")
    try {
        val nodes = MongoNodeRepository.findAllBesidesIds(res.map { NodeId(it) }.toList())
        val rows = nodes.map { listOf(it.str) }
        csvWriter().writeAll(rows, "needDeleted.csv")
    }catch (e:Exception){
        println(e)
    }
}

suspend fun deleteEN() {
    val res = getNodeIdFromCSV("res.csv")
    try {
        MongoNodeRepository.delete(res.map { NodeId(it) }.toList())
    } catch (e: Exception) {
        println(e)
    }
}


suspend fun deleteSymbol() {
    val res = getNodeIdFromCSV("res.csv")
    try {
        MongoSymbolRepository.delete(res.map { SymbolId(it) }.toList())
    } catch (e: Exception) {
        println(e)
    }
}
