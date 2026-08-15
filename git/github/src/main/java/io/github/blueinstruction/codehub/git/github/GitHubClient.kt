package io.github.blueinstruction.codehub.git.github

import kotlinx.serialization.Serializable

@Serializable
data class GitHubRepo(
    val owner: String,
    val name: String,
    val fullName: String,
    val description: String?,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val cloneUrl: String,
    val webUrl: String,
    val stars: Int,
    val openIssuesCount: Int
)

@Serializable
data class GitHubIssue(
    val number: Int,
    val title: String,
    val state: String,
    val author: String,
    val body: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val labels: List<String>
)

@Serializable
data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val state: String,
    val author: String,
    val head: String,
    val base: String,
    val merged: Boolean,
    val draft: Boolean,
    val webUrl: String
)

interface GitHubClient {
    suspend fun listRepositories(token: String, page: Int = 1, perPage: Int = 30): List<GitHubRepo>
    suspend fun getRepository(token: String, owner: String, name: String): GitHubRepo?
    suspend fun listIssues(token: String, owner: String, name: String, state: String = "open"): List<GitHubIssue>
    suspend fun listPullRequests(token: String, owner: String, name: String, state: String = "open"): List<GitHubPullRequest>
    suspend fun createIssue(token: String, owner: String, name: String, title: String, body: String?): GitHubIssue
    suspend fun createPullRequest(token: String, owner: String, name: String, title: String, head: String, base: String, body: String?): GitHubPullRequest
}
