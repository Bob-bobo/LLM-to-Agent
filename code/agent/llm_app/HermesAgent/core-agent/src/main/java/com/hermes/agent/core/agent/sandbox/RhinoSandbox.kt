package com.hermes.agent.core.agent.sandbox

import com.hermes.agent.core.agent.ToolResult
import kotlinx.coroutines.withTimeout
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import timber.log.Timber

/**
 * Rhino-based JavaScript sandbox.
 * Uses Mozilla Rhino JS engine running in interpreted mode.
 * - No OS access (Packages removed)
 * - No network access
 * - Runs on a background thread with a timeout
 * - Lightweight and fast startup
 */
class RhinoSandbox : CodeSandbox {

    override suspend fun execute(code: String, language: String, timeoutMs: Long): ToolResult {
        if (language != "javascript") {
            return ToolResult.error("Only JavaScript is supported in Rhino sandbox")
        }

        return try {
            withTimeout(timeoutMs) {
                executeRhino(code)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ToolResult.error("Code execution timed out after ${timeoutMs}ms")
        } catch (e: Exception) {
            ToolResult.error("Execution failed: ${e.message}")
        }
    }

    private fun executeRhino(code: String): ToolResult {
        val context = Context.enter()
        try {
            context.optimizationLevel = -1 // Interpreted mode (no class generation)

            val scope = context.initStandardObjects()

            // Security: Remove access to Java classes
            ScriptableObject.deleteProperty(scope, "Packages")
            ScriptableObject.deleteProperty(scope, "java")
            ScriptableObject.deleteProperty(scope, "javax")
            ScriptableObject.deleteProperty(scope, "org")
            ScriptableObject.deleteProperty(scope, "com")

            // Add a print function for output
            val outputBuffer = StringBuilder()
            val printFn = object : org.mozilla.javascript.BaseFunction() {
                override fun call(
                    cx: Context, scope: org.mozilla.javascript.Scriptable,
                    thisObj: org.mozilla.javascript.Scriptable?, args: Array<Any?>
                ): Any {
                    val text = args.joinToString(" ") { Context.toString(it) }
                    outputBuffer.appendLine(text)
                    return org.mozilla.javascript.Undefined.instance
                }
            }
            ScriptableObject.putProperty(scope, "print", printFn)

            // Execute the code
            val result = context.evaluateString(scope, code, "HermesSandbox", 1, null)

            // Build output
            val output = buildString {
                if (outputBuffer.isNotEmpty()) {
                    append(outputBuffer.toString().trimEnd())
                }
                val resultStr = Context.toString(result)
                if (resultStr != "undefined" && outputBuffer.isEmpty()) {
                    append(resultStr)
                } else if (resultStr != "undefined" && outputBuffer.isNotEmpty()) {
                    appendLine()
                    append("=> $resultStr")
                }
            }

            return ToolResult.success(output.ifEmpty { "undefined" })
        } catch (e: org.mozilla.javascript.RhinoException) {
            val errorLocation = "at line ${e.lineNumber()}, column ${e.columnNumber()}"
            return ToolResult.error("JavaScript error: ${e.message} $errorLocation")
        } catch (e: Exception) {
            Timber.e(e, "Rhino execution error")
            return ToolResult.error("Execution error: ${e.message}")
        } finally {
            Context.exit()
        }
    }
}
