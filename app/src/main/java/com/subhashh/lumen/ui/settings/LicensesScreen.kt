package com.subhashh.lumen.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

private data class Credit(val name: String, val by: String, val license: String, val usedFor: String, val url: String)

private val Credits = listOf(
    Credit("Backdrop", "Kyant0", "Apache 2.0", "The liquid glass you see everywhere in Lumen", "https://github.com/Kyant0/backdrop"),
    Credit(
        "liquid_glass_easy",
        "Ahmed Gamil",
        "MIT",
        "The motion of the glass selection pill in the tab bar",
        "https://pub.dev/packages/liquid_glass_easy",
    ),
    Credit("Telephoto", "Saket Narayan", "Apache 2.0", "Zooming and tiled loading of large photos", "https://github.com/saket/telephoto"),
    Credit("Coil", "Coil Contributors", "Apache 2.0", "Image loading", "https://github.com/coil-kt/coil"),
    Credit(
        "Jetpack Compose, Media3, AndroidX",
        "The Android Open Source Project",
        "Apache 2.0",
        "UI, video playback, EXIF and the rest of the platform pieces",
        "https://developer.android.com/jetpack",
    ),
    Credit("Kotlin and kotlinx.coroutines", "JetBrains", "Apache 2.0", "The language and its async library", "https://kotlinlang.org"),
    Credit(
        "Fluent UI System Icons",
        "Microsoft",
        "MIT",
        "Icons",
        "https://github.com/microsoft/fluentui-system-icons",
    ),
    Credit("GeoNames", "GeoNames", "CC BY 4.0", "Offline place names for photo locations", "https://www.geonames.org"),
)

@Composable
fun LicensesScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = groupColors()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        LargeTitle("Licenses")
        Text(
            "Lumen is built on the work of these open-source projects. Tap one to visit it.",
            fontSize = 15.sp,
            color = colors.secondary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
        Group("Credits") {
            Credits.forEachIndexed { index, credit ->
                if (index > 0) Divider()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, credit.url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(credit.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                    Text("${credit.by} · ${credit.license}", fontSize = 13.sp, color = colors.secondary)
                    Text(credit.usedFor, fontSize = 13.sp, color = colors.secondary)
                }
            }
        }
    }
}
