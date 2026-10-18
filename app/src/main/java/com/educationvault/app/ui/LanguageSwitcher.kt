package com.educationvault.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.educationvault.app.LanguageStore

/**
 * Two buttons: اردو | English. Each language name is deliberately written in its own
 * script (not translated) so people can always find their language.
 */
@Composable
fun LanguageSwitcher(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val current = LanguageStore.get(context)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LangButton("اردو", current == LanguageStore.URDU) { switchTo(context, LanguageStore.URDU) }
        LangButton("English", current == LanguageStore.ENGLISH) { switchTo(context, LanguageStore.ENGLISH) }
    }
}

@Composable
private fun LangButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.height(36.dp), contentPadding = padding) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.height(36.dp), contentPadding = padding) { Text(label) }
    }
}

private fun switchTo(context: Context, language: String) {
    if (LanguageStore.get(context) == language) return
    LanguageStore.set(context, language)
    // Re-creates the screen with the new language and layout direction; the login state is kept.
    context.findActivity()?.recreate()
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
