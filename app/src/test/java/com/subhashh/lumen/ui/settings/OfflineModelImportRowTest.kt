package com.subhashh.lumen.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.ModelImports
import com.subhashh.lumen.ai.ModelPack
import com.subhashh.lumen.ui.theme.LumenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfflineModelImportRowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun opensSystemPickerForMultipleDocumentsAndShowsImportFailure() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        try {
            activity.setContent {
                LumenTheme { OfflineModelImportRow() }
            }
            compose.onNodeWithText(activity.getString(R.string.smart_import_files)).performClick()
            val shadow = shadowOf(activity)
            val picker = shadow.nextStartedActivityForResult.intent
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
            assertTrue(picker.hasCategory(Intent.CATEGORY_OPENABLE))
            assertEquals("*/*", picker.type)
            assertTrue(picker.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))

            shadow.receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.parse("content://missing/model.zip")))
            val result = activity.getString(R.string.smart_import_result)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(result).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(result).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.smart_import_failed), substring = true).assertIsDisplayed()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun forwardsLooseFilesAndZipTogetherAndShowsVerificationAndReadiness() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val selected = listOf(Uri.parse("content://test/model.onnx"), Uri.parse("content://test/models.zip"))
        val received = AtomicReference<List<Uri>>()
        try {
            activity.setContent {
                LumenTheme {
                    OfflineModelImportRow { _, documents ->
                        received.set(documents)
                        ModelImports.Result(2, null, setOf(ModelPack.Search))
                    }
                }
            }
            compose.onNodeWithText(activity.getString(R.string.smart_import_files)).performClick()
            val shadow = shadowOf(activity)
            val picker = shadow.nextStartedActivityForResult.intent
            val selection = ClipData.newRawUri("Models", selected.first()).apply {
                addItem(ClipData.Item(selected.last()))
            }
            shadow.receiveResult(picker, Activity.RESULT_OK, Intent().apply { clipData = selection })
            val message = activity.resources.getQuantityString(R.plurals.smart_import_count, 2, 2)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(message, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(selected, received.get())
            compose.onNodeWithText(message, substring = true).assertIsDisplayed()
            val ready = activity.getString(R.string.smart_import_ready, activity.getString(R.string.smart_pack_search))
            compose.onNodeWithText(ready, substring = true).assertIsDisplayed()
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
