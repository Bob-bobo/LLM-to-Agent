package com.hermes.agent.core.agent.tool.builtin

import com.hermes.agent.core.agent.tool.AgentTool
import com.hermes.agent.core.agent.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Web search tool - performs HTTP GET requests to search APIs.
 * Uses a configurable search endpoint (default: DuckDuckGo instant answer API).
 */
class WebSearchTool(
    private val httpClient: OkHttpClient
) : AgentTool {
    override val name = "web_search"
    override val description = "Search the web for information. Returns search results from the configured search API."
    override val inputSchema = """{"type":"object","properties":{"query":{"type":"string","description":"Search query string"}},"required":["query"]}"""

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
            ?: return ToolResult.error("Missing 'query' parameter")

        return withContext(Dispatchers.IO) {
            try {
                // Use DuckDuckGo instant answer API (no API key needed)
                val url = "https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&no_html=1"
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext ToolResult.error("Search failed: HTTP ${response.code}")
                }

                val body = response.body?.string() ?: return@withContext ToolResult.error("Empty response")
                // Parse DuckDuckGo response (simplified)
                val abstract = extractField(body, "Abstract")
                val abstractText = extractField(body, "AbstractText")

                if (!abstractText.isNullOrBlank()) {
                    ToolResult.success(abstractText)
                } else if (!abstract.isNullOrBlank()) {
                    ToolResult.success(abstract)
                } else {
                    ToolResult.success("No instant answer found for: $query. Try a different search query.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Web search failed")
                ToolResult.error("Search failed: ${e.message}")
            }
        }
    }

    private fun extractField(json: String, field: String): String? {
        val regex = Regex("\"$field\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return regex.find(json)?.groupValues?.get(1)?.replace("\\\"", "\"")
    }
}
