package com.hermes.agent.core.agent.tool.builtin

import com.hermes.agent.core.agent.tool.AgentTool
import com.hermes.agent.core.agent.ToolResult

/**
 * File reader tool - reads file content via Android Storage Access Framework.
 * The actual file reading is delegated to the UI layer which has SAF access.
 */
class FileReaderTool : AgentTool {
    override val name = "file_reader"
    override val description = "Read the content of a file. Provide the file URI or path. For local files, the app will prompt for file selection via the system file picker."
    override val inputSchema = """{"type":"object","properties":{"uri":{"type":"string","description":"URI of the file to read"},"encoding":{"type":"string","description":"File encoding (default: UTF-8)"}},"required":["uri"]}"""

    // Callback for reading file content (set by the UI layer)
    var fileReader: (suspend (String) -> String?)? = null

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val uri = params["uri"]?.toString()
            ?: return ToolResult.error("Missing 'uri' parameter")

        val reader = fileReader
            ?: return ToolResult.error("File reader not available. Please select a file through the attachment button.")

        return try {
            val content = reader(uri)
            if (content != null) {
                // Truncate very large files
                if (content.length > 50000) {
                    ToolResult.success(content.take(50000) + "\n\n[File truncated - showing first 50000 characters]")
                } else {
                    ToolResult.success(content)
                }
            } else {
                ToolResult.error("Could not read file: $uri")
            }
        } catch (e: Exception) {
            ToolResult.error("File read error: ${e.message}")
        }
    }
}
