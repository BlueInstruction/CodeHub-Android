package io.github.blueinstruction.codehub.ai.gateway

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

class HttpProviderClient(
    override val config: AiProviderConfig
) : AiProviderClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(request: ChatRequest): Flow<ChatChunk> = flow {
        val endpoint = config.endpoint ?: throw IllegalStateException("Online provider requires endpoint")
        val body = buildRequestBody(request)
        val req = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .apply { config.apiKey?.let { header("Authorization", "Bearer $it") } }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                emit(ChatChunk.Error("HTTP ${response.code}: ${response.body?.string()?.take(500)}"))
                return@use
            }
            val text = response.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull()
            if (parsed == null) {
                emit(ChatChunk.Error("Invalid JSON response"))
                return@use
            }
            val content = parsed["choices"]?.let { it as? JsonArray }?.firstOrNull()
                ?.let { it as? JsonObject }
                ?.get("message")?.let { it as? JsonObject }
                ?.get("content")?.let { (it as? JsonPrimitive)?.content }
                ?: ""
            if (content.isNotEmpty()) {
                emit(ChatChunk.Delta(content))
            }
            emit(ChatChunk.Usage(promptTokens = 0, completionTokens = 0))
        }
    }

    private fun buildRequestBody(request: ChatRequest): String {
        val messages = buildJsonArray {
            request.messages.forEach { msg ->
                add(buildJsonObject {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }
        val body = buildJsonObject {
            put("model", request.model ?: config.defaultModel ?: "")
            put("messages", messages)
            put("temperature", request.temperature.toDouble())
            request.maxTokens?.let { put("max_tokens", it) }
        }
        return json.encodeToString(JsonObject.serializer(), body)
    }

    override suspend fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }
}
