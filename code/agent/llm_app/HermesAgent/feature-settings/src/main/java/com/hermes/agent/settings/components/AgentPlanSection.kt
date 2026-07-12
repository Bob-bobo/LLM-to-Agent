package com.hermes.agent.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.agent.core.data.model.AgentPlanConfig
import com.hermes.agent.core.data.model.PlanStrategy
import com.hermes.agent.core.data.model.SandboxType

@Composable
fun AgentPlanSection(
    planConfig: AgentPlanConfig,
    onStrategyChange: (PlanStrategy) -> Unit,
    onMaxStepsChange: (Int) -> Unit,
    onToggleTool: (String, Boolean) -> Unit,
    onSandboxChange: (SandboxType) -> Unit,
    onReflectionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Strategy selection
            Text("推理策略", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = planConfig.strategy == PlanStrategy.FUNCTION_CALLING,
                    onClick = { onStrategyChange(PlanStrategy.FUNCTION_CALLING) },
                    label = { Text("Function Calling") }
                )
                FilterChip(
                    selected = planConfig.strategy == PlanStrategy.REACT,
                    onClick = { onStrategyChange(PlanStrategy.REACT) },
                    label = { Text("ReAct") }
                )
            }

            // Max steps slider
            Text("最大推理步数: ${planConfig.maxSteps}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = planConfig.maxSteps.toFloat(),
                onValueChange = { onMaxStepsChange(it.toInt()) },
                valueRange = 1f..30f,
                steps = 29
            )

            // Enabled tools
            Text("启用工具", style = MaterialTheme.typography.titleSmall)
            val allTools = listOf(
                "calculator" to "计算器",
                "web_search" to "网络搜索",
                "file_reader" to "文件读取",
                "note" to "笔记",
                "date_time" to "日期时间",
                "clipboard" to "剪贴板",
                "code_interpreter" to "代码执行",
                "calendar" to "日历",
                "contact" to "联系人",
                "sms" to "短信",
                "phone" to "电话",
                "app_launcher" to "应用启动"
            )
            allTools.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { (toolId, toolName) ->
                        FilterChip(
                            selected = toolId in planConfig.enabledTools,
                            onClick = { onToggleTool(toolId, toolId !in planConfig.enabledTools) },
                            label = { Text(toolName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Code sandbox
            Text("代码沙箱", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = planConfig.codeSandbox == SandboxType.RHINO,
                    onClick = { onSandboxChange(SandboxType.RHINO) },
                    label = { Text("Rhino") }
                )
                FilterChip(
                    selected = planConfig.codeSandbox == SandboxType.WEBVIEW,
                    onClick = { onSandboxChange(SandboxType.WEBVIEW) },
                    label = { Text("WebView") }
                )
                FilterChip(
                    selected = planConfig.codeSandbox == SandboxType.OFF,
                    onClick = { onSandboxChange(SandboxType.OFF) },
                    label = { Text("关闭") }
                )
            }

            // Reflection toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = planConfig.reflectionEnabled,
                    onCheckedChange = onReflectionChange
                )
                Text("启用反思 (自我纠错)", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
