package io.github.blueinstruction.codehub.core.workspace.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["updatedAt"])
    ]
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val language: String,
    val buildSystem: String,
    val vcs: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val lastOpenedAt: Long? = null
)
