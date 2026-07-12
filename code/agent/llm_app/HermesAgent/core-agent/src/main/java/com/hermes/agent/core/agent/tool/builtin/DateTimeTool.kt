package com.hermes.agent.core.agent.tool.builtin

import com.hermes.agent.core.agent.tool.AgentTool
import com.hermes.agent.core.agent.ToolResult
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Date/Time tool - returns current date, time, and timezone info.
 */
class DateTimeTool : AgentTool {
    override val name = "date_time"
    override val description = "Get the current date, time, timezone, and day of week. Useful for time-sensitive tasks."
    override val inputSchema = """{"type":"object","properties":{"timezone":{"type":"string","description":"Optional timezone ID, e.g. 'Asia/Shanghai', 'America/New_York'. Defaults to system timezone."}}}"""

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val tzId = params["timezone"]?.toString()
        val zoneId = if (tzId != null) {
            try { ZoneId.of(tzId) } catch (e: Exception) {
                return ToolResult.error("Invalid timezone: $tzId")
            }
        } else ZoneId.systemDefault()

        val now = ZonedDateTime.now(zoneId)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val result = buildString {
            appendLine("Current Date & Time: ${now.format(formatter)}")
            appendLine("Timezone: ${now.zone.id} (${now.zone.getDisplayName(TextStyle.FULL, Locale.ENGLISH)})")
            appendLine("Day of Week: ${now.dayOfWeek.name}")
            appendLine("Unix Timestamp: ${now.toEpochSecond()}")
        }
        return ToolResult.success(result.trimEnd())
    }
}
