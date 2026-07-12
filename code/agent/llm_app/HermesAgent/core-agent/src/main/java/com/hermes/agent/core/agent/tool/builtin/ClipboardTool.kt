package com.hermes.agent.core.agent.tool.builtin

import com.hermes.agent.core.agent.tool.AgentTool
import com.hermes.agent.core.agent.ToolResult

/**
 * Clipboard tool - read and write the device clipboard.
 */
class ClipboardTool : AgentTool {
    override val name = "clipboard"
    override val description = "Read from or write to the device clipboard. Use action 'read' to get clipboard content, or 'write' with 'content' to set it."
    override val inputSchema = """{"type":"object","properties":{"action":{"type":"string","enum":["read","write"],"description":"Action to perform: 'read' or 'write'"},"content":{"type":"string","description":"Content to write to clipboard (only for 'write' action)"}},"required":["action"]}"""

    // ClipboardManager will be injected at a higher level
    var clipboardManager: android.content.ClipboardManager? = null

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val action = params["action"]?.toString()
            ?: return ToolResult.error("Missing 'action' parameter")

        val manager = clipboardManager
            ?: return ToolResult.error("Clipboard not available")

        return when (action) {
            "read" -> {
                if (manager.hasPrimaryClip()) {
                    val clip = manager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    ToolResult.success(clip.ifEmpty { "Clipboard is empty" })
                } else {
                    ToolResult.success("Clipboard is empty")
                }
            }
            "write" -> {
                val content = params["content"]?.toString()
                    ?: return ToolResult.error("Missing 'content' for write action")
                manager.setPrimaryClip(
                    android.content.ClipData.newPlainText("HermesAgent", content)
                )
                ToolResult.success("Copied to clipboard: ${content.take(100)}")
            }
            else -> ToolResult.error("Unknown action: $action. Use 'read' or 'write'.")
        }
    }
}
