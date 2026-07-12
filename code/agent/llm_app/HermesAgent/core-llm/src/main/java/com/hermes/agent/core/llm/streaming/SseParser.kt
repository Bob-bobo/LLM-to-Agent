package com.hermes.agent.core.llm.streaming

/**
 * Lightweight Server-Sent Events (SSE) parser.
 * Parses SSE format lines from an OkHttp streaming response body.
 *
 * SSE format:
 *   data: {"content":"hello"}
 *   data: {"content":" world"}
 *   data: [DONE]
 *
 *   event: ping
 *   data: {}
 */
class SseParser {
    private val currentData = StringBuilder()
    private var currentEvent = "message"

    /**
     * Parse a single line from the SSE stream.
     * Returns a list of completed events (usually 0 or 1).
     * An event is dispatched when an empty line is encountered.
     */
    fun parseLine(line: String): List<SseEvent> {
        // Empty line = dispatch event
        if (line.isEmpty()) {
            val events = if (currentData.isNotEmpty()) {
                listOf(SseEvent(event = currentEvent, data = currentData.toString().trimEnd()))
            } else {
                emptyList()
            }
            currentData.clear()
            currentEvent = "message"
            return events
        }

        // Data line
        if (line.startsWith("data: ")) {
            currentData.appendLine(line.removePrefix("data: "))
        } else if (line.startsWith("data:")) {
            // Some servers omit the space
            currentData.appendLine(line.removePrefix("data:"))
        }
        // Event type line
        else if (line.startsWith("event: ")) {
            currentEvent = line.removePrefix("event: ")
        }
        // Ignore "id:", "retry:", and comment lines (starting with ":")

        return emptyList()
    }

    /**
     * Reset parser state (e.g., for reuse).
     */
    fun reset() {
        currentData.clear()
        currentEvent = "message"
    }
}

/**
 * A parsed SSE event.
 */
data class SseEvent(
    val event: String,
    val data: String
) {
    /** Whether this is the terminal [DONE] event */
    val isDone: Boolean get() = data.trim() == "[DONE]"
}

/**
 * Buffer for accumulating partial tool call arguments during streaming.
 * Tool call arguments arrive as JSON fragments and need to be concatenated.
 */
class StreamBuffer {
    private val buffers = mutableMapOf<String, StringBuilder>()
    private val names = mutableMapOf<String, String>()

    fun appendArguments(toolCallId: String, delta: String) {
        buffers.getOrPut(toolCallId) { StringBuilder() }.append(delta)
    }

    fun setName(toolCallId: String, name: String) {
        names[toolCallId] = name
    }

    fun getCompleteArguments(toolCallId: String): String? {
        return buffers[toolCallId]?.toString()
    }

    fun getName(toolCallId: String): String? {
        return names[toolCallId]
    }

    fun clear(toolCallId: String) {
        buffers.remove(toolCallId)
        names.remove(toolCallId)
    }

    fun clearAll() {
        buffers.clear()
        names.clear()
    }
}
