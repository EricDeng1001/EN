package model


import kotlinx.serialization.Serializable


interface Horizon {

    suspend fun queryUserInfoByToken(token: String): User?

}

@Serializable
data class User(
    val id: Long,
    val username: String,
    val password: String? = null,
    val nickname: String,
    val status: String,
    val email: String,
    val roles: List<String>,
    val groups: List<String>
) {
    fun isAdmin(): Boolean{
        return roles.contains("computing-admin")
    }
}

@Serializable
data class HorizonConfig(
    val http: Boolean,
    val host: String,
    val port: Int,
    val url: Url,
    val username: String,
    val password: String
) {

    @Serializable
    data class Url(val login: String, val users: String, val me: String)
}