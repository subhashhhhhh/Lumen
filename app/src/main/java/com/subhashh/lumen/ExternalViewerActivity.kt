package com.subhashh.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.subhashh.lumen.data.ExternalMedia
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.setLumenContent
import com.subhashh.lumen.ui.viewer.ViewerScreen

/**
 * Opens one photo or video another app handed us: "Open with Lumen" from a chat app, file
 * manager or browser, or the camera's review button. Runs in the sender's task, like other
 * gallery viewers, and closes back to it.
 */
class ExternalViewerActivity : ComponentActivity() {

    private sealed interface Load {
        data object Loading : Load
        data object Failed : Load
        data class Ready(val item: MediaItem) : Load
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val type = intent?.type
        setLumenContent {
            val load by produceState<Load>(Load.Loading, uri) {
                value = uri?.let { ExternalMedia.item(this@ExternalViewerActivity, it, type) }
                    ?.let { Load.Ready(it) } ?: Load.Failed
            }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (val state = load) {
                    Load.Loading -> Unit
                    Load.Failed -> Text(
                        stringResource(R.string.app_cant_open_file),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                    is Load.Ready -> ViewerScreen(
                        items = listOf(state.item),
                        initialIndex = 0,
                        onClose = { finish() },
                        onMediaChanged = {},
                        external = true,
                    )
                }
            }
        }
    }
}
