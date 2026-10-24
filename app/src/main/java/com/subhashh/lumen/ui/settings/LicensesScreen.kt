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
        "avif-coder (with libavif and dav1d)",
        "Radzivon Bartoshyk",
        "MIT · BSD",
        "AVIF photos, on phones whose own decoder can't show them",
        "https://github.com/awxkee/avif-coder",
    ),
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
    Credit(
        "SigLIP 2",
        "Google",
        "Apache 2.0",
        "Smart Search, Accurate: the model that understands what's in photos",
        "https://huggingface.co/google/siglip2-base-patch16-224",
    ),
    Credit(
        "OpenCLIP ViT-B/32 (LAION-2B) and CLIP's tokenizer",
        "LAION, OpenCLIP authors, OpenAI",
        "MIT",
        "Smart Search, Fast",
        "https://github.com/mlfoundations/open_clip",
    ),
    Credit(
        "Immich",
        "Immich contributors",
        "AGPL-3.0 (models: their original licenses)",
        "The ONNX exports Lumen's search models are converted from; no Immich code is used",
        "https://huggingface.co/immich-app",
    ),
    Credit(
        "PP-OCRv6 small",
        "PaddlePaddle authors (Baidu)",
        "Apache 2.0",
        "Reading text in photos and videos",
        "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx",
    ),
    Credit("YuNet", "Shiqi Yu (OpenCV Zoo)", "MIT", "Finding faces", "https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet"),
    Credit(
        "SFace",
        "Zhong et al. (OpenCV Zoo)",
        "Apache 2.0",
        "Telling faces apart, to group them into people",
        "https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface",
    ),
    Credit(
        "Whistle and the needle engine",
        "Cactus Compute",
        "Apache 2.0",
        "Subtitles: speech to text on the phone",
        "https://huggingface.co/Cactus-Compute/whistle",
    ),
    Credit("Silero VAD", "Silero Team", "MIT", "Subtitles: finding where people speak", "https://github.com/snakers4/silero-vad"),
    Credit("ONNX Runtime", "Microsoft", "MIT", "Runs most of Lumen's models on the phone", "https://github.com/microsoft/onnxruntime"),
    Credit("LiteRT", "Google", "Apache 2.0", "Runs models on the phone's GPU", "https://github.com/google-ai-edge/LiteRT"),
    Credit("Recognize Anything", "Xinyu Huang et al.", "Apache 2.0", "The words for \"Looks like\" and search suggestions", "https://github.com/xinyu1205/recognize-anything"),
    Credit("MapLibre Native", "MapLibre contributors", "BSD 2-Clause", "Draws the maps in photo details", "https://github.com/maplibre/maplibre-native"),
    Credit("OpenFreeMap", "Zsolt Ero", "MIT (styles), free tile hosting", "The map tiles and styles", "https://openfreemap.org"),
    Credit("OpenMapTiles", "MapTiler and contributors", "BSD 3-Clause / CC-BY 4.0", "The map tiles' schema and style design", "https://openmaptiles.org"),
    Credit("OpenStreetMap", "OpenStreetMap contributors", "ODbL", "The map data", "https://www.openstreetmap.org/copyright"),
)

@Composable
fun LicensesScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = groupColors()
    SettingsPage(contentPadding, modifier) {
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
