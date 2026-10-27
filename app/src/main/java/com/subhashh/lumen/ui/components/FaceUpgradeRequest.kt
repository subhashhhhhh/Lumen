package com.subhashh.lumen.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.FaceModel
import com.subhashh.lumen.ai.Models
import com.subhashh.lumen.data.LumenPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Once, for someone whose faces were read before 0.3 on a phone whose NPU runs Accurate: offers the
 * better face model (a bigger download, and faces read again). Update chooses Accurate; Not Now,
 * Fast (small, on the CPU, still well ahead of the old model). Either way the download starts on
 * Wi-Fi; Settings can switch later. Shown at the same quiet moments as [StarRequest].
 */
@Composable
fun FaceUpgradeRequest(prefs: LumenPrefs, canShow: Boolean, show: (Confirmation) -> Unit) {
    val context = LocalContext.current
    val due by produceState(false) { value = withContext(Dispatchers.IO) { Models.faceUpgradeToAsk(context) } }
    if (due && canShow) {
        LaunchedEffect(Unit) {
            delay(2_000)
            if (prefs.faceModel != null) return@LaunchedEffect
            val size = FaceModel.accurate(context)?.let { FaceModel.bytes(it) } ?: return@LaunchedEffect
            fun choose(id: String) {
                prefs.faceModel = id
                Models.fetchNpuFiles(context)
            }
            show(
                Confirmation(
                    title = context.getString(R.string.main_faces_upgrade_title),
                    message = context.getString(R.string.main_faces_upgrade_message, "%.0f MB".format(size / 1e6)),
                    confirm = context.getString(R.string.main_faces_upgrade_confirm),
                    cancel = context.getString(R.string.main_faces_upgrade_later),
                    onCancel = { choose(FaceModel.Fast.id) },
                ) { choose(FaceModel.AccurateId) },
            )
        }
    }
}
