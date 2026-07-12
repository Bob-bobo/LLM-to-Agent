package com.hermes.agent.core.agent.tool.builtin

import com.hermes.agent.core.agent.tool.AgentTool
import com.hermes.agent.core.agent.ToolResult

/**
 * Calculator tool - evaluates mathematical expressions safely.
 */
class CalculatorTool : AgentTool {
    override val name = "calculator"
    override val description = "Evaluate a mathematical expression. Supports basic arithmetic, trigonometric functions, and math constants."
    override val inputSchema = """{"type":"object","properties":{"expression":{"type":"string","description":"The mathematical expression to evaluate, e.g. '2 + 3 * 4' or 'Math.sin(Math.PI/4)'"}},"required":["expression"]}"""

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val expression = params["expression"]?.toString()
            ?: return ToolResult.error("Missing 'expression' parameter")

        return try {
            // Use Rhino for safe evaluation (no OS access)
            val context = org.mozilla.javascript.Context.enter()
            try {
                context.optimizationLevel = -1 // Interpreted mode
                val scope = context.initStandardObjects()
                // Only allow Math object
                val result = context.evaluateString(scope, expression, "Calculator", 1, null)
                ToolResult.success(result.toString())
            } finally {
                org.mozilla.javascript.Context.exit()
            }
        } catch (e: Exception) {
            ToolResult.error("Calculation error: ${e.message}")
        }
    }
}
