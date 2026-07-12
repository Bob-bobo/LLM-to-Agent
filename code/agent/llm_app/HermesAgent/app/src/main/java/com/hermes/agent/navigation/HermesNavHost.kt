package com.hermes.agent.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hermes.agent.chat.ChatScreen
import com.hermes.agent.chat.ChatViewModel
import com.hermes.agent.settings.SettingsScreen
import com.hermes.agent.settings.SettingsViewModel

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val CHAT_WITH_ID = "chat/{conversationId}"
}

@Composable
fun HermesNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CHAT,
        modifier = modifier
    ) {
        composable(Routes.CHAT) {
            val viewModel: ChatViewModel = hiltViewModel()
            ChatScreen(
                viewModel = viewModel,
                onNewConversation = { /* TODO: Create new conversation */ },
                onShowConversations = { /* TODO: Show conversation list */ }
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(viewModel = viewModel)
        }
    }
}
