package com.subhashh.lumen

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.LumenApp
import com.subhashh.lumen.ui.PickRequest
import com.subhashh.lumen.ui.setLumenContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pick = PickRequest.from(intent)
        // Software Update: once a day, if it's on (Settings → About).
        if (savedInstanceState == null) com.subhashh.lumen.ui.settings.UpdateSession.checkIfDue(applicationContext)
        setLumenContent {
            LumenApp(
                pick = pick,
                onPicked = ::returnPicked,
                onCancelPick = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                },
            )
        }
    }

    /** Hands the chosen items back to the app that asked, with permission to read them. */
    private fun returnPicked(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val clip = ClipData.newUri(contentResolver, "Lumen", items.first().uri)
        items.drop(1).forEach { clip.addItem(ClipData.Item(it.uri)) }
        val result = Intent().apply {
            data = items.first().uri
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
