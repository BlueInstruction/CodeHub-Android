package codehub.ai.context

interface ContextRetriever {
    suspend fun retrieve(workspaceId: String, query: String): List<String>
    suspend fun refresh(workspaceId: String)
}

interface ContextScope {
    val name: String
    suspend fun gather(workspaceId: String, query: String): List<String>
}
