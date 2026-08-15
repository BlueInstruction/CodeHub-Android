package com.codehub.core.workspace

interface ProjectManager {
    fun createProject(name: String, path: String, description: String?): Project
    fun openProject(path: String): Project
    fun closeProject(): Unit
    fun getCurrentProject(): Project?
    fun getRecentProjects(): List<Project>
    fun deleteProject(project: Project): Boolean
}