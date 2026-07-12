package com.hermes.agent.di

import com.hermes.agent.core.agent.planner.FunctionCallingPlanner
import com.hermes.agent.core.agent.planner.Planner
import com.hermes.agent.core.agent.planner.ReActPlanner
import com.hermes.agent.core.agent.tool.AgentTool
import com.hermes.agent.core.agent.tool.ToolRegistry
import com.hermes.agent.core.agent.tool.builtin.CalculatorTool
import com.hermes.agent.core.agent.tool.builtin.ClipboardTool
import com.hermes.agent.core.agent.tool.builtin.CodeInterpreterTool
import com.hermes.agent.core.agent.tool.builtin.DateTimeTool
import com.hermes.agent.core.agent.tool.builtin.FileReaderTool
import com.hermes.agent.core.agent.tool.builtin.NoteTool
import com.hermes.agent.core.agent.tool.builtin.WebSearchTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @Singleton
    fun providePlanner(functionCallingPlanner: FunctionCallingPlanner): Planner =
        functionCallingPlanner

    @Provides
    @Singleton
    fun provideToolRegistry(
        calculatorTool: CalculatorTool,
        dateTimeTool: DateTimeTool,
        noteTool: NoteTool,
        clipboardTool: ClipboardTool,
        fileReaderTool: FileReaderTool,
        codeInterpreterTool: CodeInterpreterTool,
        webSearchTool: WebSearchTool
    ): ToolRegistry {
        val registry = ToolRegistry()
        registry.registerAll(listOf(
            calculatorTool,
            dateTimeTool,
            noteTool,
            clipboardTool,
            fileReaderTool,
            codeInterpreterTool,
            webSearchTool
        ))
        return registry
    }

    @Provides
    @Singleton
    fun provideCalculatorTool(): CalculatorTool = CalculatorTool()

    @Provides
    @Singleton
    fun provideDateTimeTool(): DateTimeTool = DateTimeTool()

    @Provides
    @Singleton
    fun provideNoteTool(): NoteTool = NoteTool()

    @Provides
    @Singleton
    fun provideClipboardTool(): ClipboardTool = ClipboardTool()

    @Provides
    @Singleton
    fun provideFileReaderTool(): FileReaderTool = FileReaderTool()

    @Provides
    @Singleton
    fun provideCodeInterpreterTool(): CodeInterpreterTool = CodeInterpreterTool()

    @Provides
    @Singleton
    fun provideWebSearchTool(okHttpClient: OkHttpClient): WebSearchTool =
        WebSearchTool(okHttpClient)
}
