package codehub.git.github

import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.content
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpGitHubClient @Inject constructor(
    private val diagnostics: DiagnosticSink
) : GitHubClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://api.github.com"

    override suspend fun listRepositories(token: String, page: Int, perPage: Int): List<GitHubRepo> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/user/repos?page=$page&per_page=$perPage&sort=updated"
        val arr = performGetArray(url, token)
        arr.map { it.jsonObject }.map { parseRepo(it) }
    }

    override suspend fun getRepository(token: String, owner: String, name: String): GitHubRepo? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/repos/$owner/$name"
        runCatching { performGetObject(url, token) }.getOrNull()?.let { parseRepo(it) }
    }

    override suspend fun listIssues(token: String, owner: String, name: String, state: String): List<GitHubIssue> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/repos/$owner/$name/issues?state=$state"
        val arr = performGetArray(url, token)
        arr.map { it.jsonObject }
            .filter { !it.containsKey("pull_request") }
            .map { parseIssue(it) }
    }

    override suspend fun listPullRequests(token: String, owner: String, name: String, state: String): List<GitHubPullRequest> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/repos/$owner/$name/pulls?state=$state"
        val arr = performGetArray(url, token)
        arr.map { it.jsonObject }.map { parsePull(it) }
    }

    override suspend fun createIssue(token: String, owner: String, name: String, title: String, body: String?): GitHubIssue = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("title", title)
            body?.let { put("body", it) }
        }
        val url = "$baseUrl/repos/$owner/$name/issues"
        val obj = performPostObject(url, token, json.encodeToString(JsonObject.serializer(), payload))
        parseIssue(obj)
    }

    override suspend fun createPullRequest(token: String, owner: String, name: String, title: String, head: String, base: String, body: String?): GitHubPullRequest = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("title", title)
            put("head", head)
            put("base", base)
            body?.let { put("body", it) }
        }
        val url = "$baseUrl/repos/$owner/$name/pulls"
        val obj = performPostObject(url, token, json.encodeToString(JsonObject.serializer(), payload))
        parsePull(obj)
    }

    private fun performGetArray(url: String, token: String): JsonArray {
        val req = Request.Builder().url(url).header("Accept", "application/vnd.github+json").header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.RuntimeInitialization,
                        severity = DiagnosticSeverity.Warn,
                        status = DiagnosticStatus.Failed,
                        source = "HttpGitHubClient",
                        message = "GitHub API GET $url failed: ${response.code}"
                    )
                )
                return JsonArray(emptyList())
            }
            val text = response.body?.string().orEmpty()
            return json.parseToJsonElement(text) as JsonArray
        }
    }

    private fun performGetObject(url: String, token: String): JsonObject {
        val req = Request.Builder().url(url).header("Accept", "application/vnd.github+json").header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${text.take(200)}")
            return json.parseToJsonElement(text) as JsonObject
        }
    }

    private fun performPostObject(url: String, token: String, body: String): JsonObject {
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${text.take(200)}")
            return json.parseToJsonElement(text) as JsonObject
        }
    }

    private fun parseRepo(o: JsonObject): GitHubRepo {
        val owner = (o["owner"] as? JsonObject)?.get("login")?.jsonPrimitive?.contentOrNull ?: ""
        val name = o["name"]?.jsonPrimitive?.contentOrNull ?: ""
        return GitHubRepo(
            owner = owner,
            name = name,
            fullName = o["full_name"]?.jsonPrimitive?.contentOrNull ?: "$owner/$name",
            description = o["description"]?.jsonPrimitive?.contentOrNull,
            isPrivate = o["private"]?.jsonPrimitive?.contentOrNull == "true",
            defaultBranch = o["default_branch"]?.jsonPrimitive?.contentOrNull ?: "main",
            cloneUrl = o["clone_url"]?.jsonPrimitive?.contentOrNull ?: "",
            webUrl = o["html_url"]?.jsonPrimitive?.contentOrNull ?: "",
            stars = o["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0,
            openIssuesCount = o["open_issues_count"]?.jsonPrimitive?.intOrNull ?: 0
        )
    }

    private fun parseIssue(o: JsonObject): GitHubIssue {
        return GitHubIssue(
            number = o["number"]?.jsonPrimitive?.intOrNull ?: 0,
            title = o["title"]?.jsonPrimitive?.contentOrNull ?: "",
            state = o["state"]?.jsonPrimitive?.contentOrNull ?: "",
            author = (o["user"] as? JsonObject)?.get("login")?.jsonPrimitive?.contentOrNull ?: "",
            body = o["body"]?.jsonPrimitive?.contentOrNull,
            createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull?.let { parseIso8601(it) } ?: 0L,
            updatedAt = o["updated_at"]?.jsonPrimitive?.contentOrNull?.let { parseIso8601(it) } ?: 0L,
            labels = ((o["labels"] as? JsonArray) ?: JsonArray(emptyList()))
                .mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
        )
    }

    private fun parsePull(o: JsonObject): GitHubPullRequest {
        return GitHubPullRequest(
            number = o["number"]?.jsonPrimitive?.intOrNull ?: 0,
            title = o["title"]?.jsonPrimitive?.contentOrNull ?: "",
            state = o["state"]?.jsonPrimitive?.contentOrNull ?: "",
            author = (o["user"] as? JsonObject)?.get("login")?.jsonPrimitive?.contentOrNull ?: "",
            head = (o["head"] as? JsonObject)?.get("ref")?.jsonPrimitive?.contentOrNull ?: "",
            base = (o["base"] as? JsonObject)?.get("ref")?.jsonPrimitive?.contentOrNull ?: "",
            merged = o["merged"]?.jsonPrimitive?.contentOrNull == "true",
            draft = o["draft"]?.jsonPrimitive?.contentOrNull == "true",
            webUrl = o["html_url"]?.jsonPrimitive?.contentOrNull ?: ""
        )
    }

    private fun parseIso8601(value: String): Long {
        return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }
}
