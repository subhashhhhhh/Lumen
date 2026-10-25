package com.subhashh.lumen.ui.settings

import android.content.Intent
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.subhashh.lumen.R

private data class Credit(val name: String, val by: String, val license: String, @StringRes val usedFor: Int, val url: String)

private val Credits = listOf(
    Credit("Backdrop", "Kyant0", "Apache 2.0", R.string.licenses_used_backdrop, "https://github.com/Kyant0/backdrop"),
    Credit(
        "liquid_glass_easy",
        "Ahmed Gamil",
        "MIT",
        R.string.licenses_used_liquid_glass_easy,
        "https://pub.dev/packages/liquid_glass_easy",
    ),
    Credit("Telephoto", "Saket Narayan", "Apache 2.0", R.string.licenses_used_telephoto, "https://github.com/saket/telephoto"),
    Credit("Coil", "Coil Contributors", "Apache 2.0", R.string.licenses_used_coil, "https://github.com/coil-kt/coil"),
    Credit(
        "avif-coder (with libavif and dav1d)",
        "Radzivon Bartoshyk",
        "MIT · BSD",
        R.string.licenses_used_avif,
        "https://github.com/awxkee/avif-coder",
    ),
    Credit(
        "Jetpack Compose, Media3, AndroidX",
        "The Android Open Source Project",
        "Apache 2.0",
        R.string.licenses_used_androidx,
        "https://developer.android.com/jetpack",
    ),
    Credit("Kotlin and kotlinx.coroutines", "JetBrains", "Apache 2.0", R.string.licenses_used_kotlin, "https://kotlinlang.org"),
    Credit(
        "Fluent UI System Icons",
        "Microsoft",
        "MIT",
        R.string.licenses_used_icons,
        "https://github.com/microsoft/fluentui-system-icons",
    ),
    Credit("GeoNames", "GeoNames", "CC BY 4.0", R.string.licenses_used_geonames, "https://www.geonames.org"),
    Credit(
        "SigLIP 2",
        "Google",
        "Apache 2.0",
        R.string.licenses_used_siglip,
        "https://huggingface.co/google/siglip2-base-patch16-224",
    ),
    Credit(
        "OpenCLIP ViT-B/32 (LAION-2B) and CLIP's tokenizer",
        "LAION, OpenCLIP authors, OpenAI",
        "MIT",
        R.string.licenses_used_openclip,
        "https://github.com/mlfoundations/open_clip",
    ),
    Credit(
        "Immich",
        "Immich contributors",
        "AGPL-3.0 (models: their original licenses)",
        R.string.licenses_used_immich,
        "https://huggingface.co/immich-app",
    ),
    Credit(
        "PP-OCRv6 small",
        "PaddlePaddle authors (Baidu)",
        "Apache 2.0",
        R.string.licenses_used_ocr,
        "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx",
    ),
    Credit("YuNet", "Shiqi Yu (OpenCV Zoo)", "MIT", R.string.licenses_used_yunet, "https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet"),
    Credit(
        "SFace",
        "Zhong et al. (OpenCV Zoo)",
        "Apache 2.0",
        R.string.licenses_used_sface,
        "https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface",
    ),
    Credit(
        "Whistle and the needle engine",
        "Cactus Compute",
        "Apache 2.0",
        R.string.licenses_used_whistle,
        "https://huggingface.co/Cactus-Compute/whistle",
    ),
    Credit("Silero VAD", "Silero Team", "MIT", R.string.licenses_used_silero, "https://github.com/snakers4/silero-vad"),
    Credit("ONNX Runtime", "Microsoft", "MIT", R.string.licenses_used_onnx_runtime, "https://github.com/microsoft/onnxruntime"),
    Credit("LiteRT", "Google", "Apache 2.0", R.string.licenses_used_litert, "https://github.com/google-ai-edge/LiteRT"),
    Credit("Recognize Anything", "The Recognize Anything authors", "Apache 2.0", R.string.licenses_used_ram, "https://github.com/xinyu1205/recognize-anything"),
    Credit("MapLibre Native", "MapLibre contributors", "BSD 2-Clause", R.string.licenses_used_maplibre, "https://github.com/maplibre/maplibre-native"),
    Credit("OpenFreeMap", "Zsolt Ero", "MIT (styles), free tile hosting", R.string.licenses_used_openfreemap, "https://openfreemap.org"),
    Credit("OpenMapTiles", "MapTiler and contributors", "BSD 3-Clause / CC-BY 4.0", R.string.licenses_used_openmaptiles, "https://openmaptiles.org"),
    Credit("OpenStreetMap", "OpenStreetMap contributors", "ODbL", R.string.licenses_used_osm, "https://www.openstreetmap.org/copyright"),
)

@Composable
fun LicensesScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = groupColors()
    SettingsPage(contentPadding, modifier) {
        LargeTitle(stringResource(R.string.licenses_title))
        Text(
            stringResource(R.string.licenses_intro),
            fontSize = 15.sp,
            color = colors.secondary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
        Group(stringResource(R.string.licenses_credits)) {
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
                    Text(stringResource(credit.usedFor), fontSize = 13.sp, color = colors.secondary)
                }
            }
        }
    }
}
