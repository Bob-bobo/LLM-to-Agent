package com.hermes.agent.core.agent.tool.builtin

import com.hermes.agent.core.agent.tool.AgentTool
import com.hermes.agent.core.agent.ToolResult
import com.hermes.agent.core.agent.sandbox.CodeSandbox
import com.hermes.agent.core.agent.sandbox.RhinoSandbox

/**
 * Code interpreter tool - executes JavaScript code in a sandboxed environment.
 */
class CodeInterpreterTool(
    private val sandbox: CodeSandbox = RhinoSandbox()
) : AgentTool {
    override val name = "code_interpreter"
    override val description = "Execute JavaScript code in a sandboxed environment. The code runs with no OS access and a timeout of 10 seconds. Returns the result or error message."
    override val inputSchema = """{"type":"object","properties":{"code":{"type":"string","description":"JavaScript code to execute"},"language":{"type":"string","enum":["javascript"],"description":"Programming language (currently only JavaScript is supported)"},"timeout_ms":{"type":"number","description":"Execution timeout in milliseconds (default: 10000, max: 30000)"}},"required":["code"]}"""

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val code = params["code"]?.toString()
            ?: return ToolResult.error("Missing 'code' parameter")

        val language = (params["language"]?.toString() ?: "javascript").lowercase()
        if (language != "javascript") {
            return ToolResult.error("Unsupported language: $language. Only JavaScript is supported.")
        }

        val timeoutMs = (params["timeout_ms"] as? Number)?.toLong()?.coerceIn(1000, 30000)
            ?: 10000L

        return sandbox.execute(code, language, timeoutMs)
    }
}
