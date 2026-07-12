package com.hermes.agent.core.agent.tool.builtin

import com.hermes.agent.core.agent.tool.AgentTool
import com.hermes.agent.core.agent.ToolResult

/**
 * Note tool - create, read, and list local notes.
 * Notes are stored in memory (could be backed by Room DB in production).
 */
class NoteTool : AgentTool {
    override val name = "note"
    override val description = "Manage local notes. Actions: 'create' (title, content), 'read' (title), 'list', 'delete' (title). Notes persist during the session."
    override val inputSchema = """{"type":"object","properties":{"action":{"type":"string","enum":["create","read","list","delete"],"description":"Action to perform"},"title":{"type":"string","description":"Note title"},"content":{"type":"string","description":"Note content (for create action)"}},"required":["action"]}"""

    private val notes = mutableMapOf<String, String>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val action = params["action"]?.toString()
            ?: return ToolResult.error("Missing 'action' parameter")

        return when (action) {
            "create" -> {
                val title = params["title"]?.toString()
                    ?: return ToolResult.error("Missing 'title' for create")
                val content = params["content"]?.toString()
                    ?: return ToolResult.error("Missing 'content' for create")
                notes[title] = content
                ToolResult.success("Note '$title' created successfully")
            }
            "read" -> {
                val title = params["title"]?.toString()
                    ?: return ToolResult.error("Missing 'title' for read")
                val content = notes[title]
                    ?: return ToolResult.error("Note '$title' not found")
                ToolResult.success(content)
            }
            "list" -> {
                if (notes.isEmpty()) {
                    ToolResult.success("No notes found")
                } else {
                    val list = notes.keys.mapIndexed { i, title ->
                        "${i + 1}. $title"
                    }.joinToString("\n")
                    ToolResult.success("Notes:\n$list")
                }
            }
            "delete" -> {
                val title = params["title"]?.toString()
                    ?: return ToolResult.error("Missing 'title' for delete")
                if (notes.remove(title) != null) {
                    ToolResult.success("Note '$title' deleted")
                } else {
                    ToolResult.error("Note '$title' not found")
                }
            }
            else -> ToolResult.error("Unknown action: $action")
        }
    }
}
