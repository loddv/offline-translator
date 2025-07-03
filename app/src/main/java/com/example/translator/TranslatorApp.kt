package com.example.translator


import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun TranslatorApp(configForLang: (Language, Language) -> String, initialText:String) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            Greeting(
                configForLang = configForLang,
                onManageLanguages = { navController.navigate("language_manager")                },
                initialText = initialText
            )
        }
        composable("language_manager") {
            LanguageManagerScreen()
        }
    }
}