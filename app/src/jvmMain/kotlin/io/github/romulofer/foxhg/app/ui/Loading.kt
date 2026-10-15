package io.github.romulofer.foxhg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The screen shown while something loads: a turning ring and what is loading. The ring is drawn by the UI thread, so it keeps moving while the work happens elsewhere. */
@Composable
fun LoadingScreen(what: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().testTag("loading"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(44.dp), strokeWidth = 4.dp)
            Text("Loading $what…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Builds a value with [load] on a background thread and shows [LoadingScreen] until it is ready, then [content]. The
 * value is built again when [key] changes. Used for editors, whose models read and decode a lot of the ROM when they open.
 */
@Composable
fun <T : Any> Loading(key: Any?, what: String, load: () -> T, content: @Composable (T) -> Unit) {
    val loaded: T? by produceState<T?>(null, key) { value = withContext(Dispatchers.Default) { load() } }
    val ready = loaded
    if (ready == null) LoadingScreen(what) else content(ready)
}
