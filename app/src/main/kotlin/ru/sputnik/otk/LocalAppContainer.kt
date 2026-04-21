package ru.sputnik.otk

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided. Wrap your content in CompositionLocalProvider(LocalAppContainer provides ...).")
}
