package com.hermes.agent.core.agent.sandbox

import com.hermes.agent.core.agent.ToolResult

/**
 * Interface for code execution sandboxes.
 */
interface CodeSandbox {
    suspend fun execute(code: String, language: String, timeoutMs: Long): ToolResult
}
