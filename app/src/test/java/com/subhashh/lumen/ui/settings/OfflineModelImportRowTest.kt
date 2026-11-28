package com.subhashh.lumen.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.performClick
import com.subhashh.lumen.R
import com.subhashh.lumen.ai.ModelImports
import com.subhashh.lumen.ai.ModelPack
import com.subhashh.lumen.ui.theme.LumenTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.FileNotFoundException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfflineModelImportRowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val zip = Uri.parse("content://test/models.zip")

    @After fun closeScope() = scope.cancel()

    @Test fun opensSystemPickerForMultipleDocumentsAndShowsImportFailure() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var received = emptyList<Uri>()
        val session = ModelImportSession(scope) { context, documents ->
            assertSame(activity.applicationContext, context)
            received = documents
            ModelImports.Result(0, ModelImports.Rejection("models.zip", ModelImports.Error.Archive), emptySet())
        }
        try {
            activity.setContent {
                LumenTheme { OfflineModelImportRow(session) }
            }
            compose.onNodeWithText(activity.getString(R.string.smart_import_files)).performClick()
            val shadow = shadowOf(activity)
            val picker = shadow.nextStartedActivityForResult.intent
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
            assertTrue(picker.hasCategory(Intent.CATEGORY_OPENABLE))
            assertEquals("*/*", picker.type)
            assertTrue(picker.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))

            shadow.receiveResult(picker, Activity.RESULT_OK, Intent().setData(zip))
            val result = activity.getString(R.string.smart_import_result)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(result).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(result).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.smart_import_failed), substring = true).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.smart_import_archive), substring = true).assertIsDisplayed()
            assertEquals(listOf(zip), received)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun forwardsLooseFilesAndZipTogetherAndShowsVerificationAndReadiness() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val selected = listOf(Uri.parse("content://test/model.onnx"), zip)
        var received = emptyList<Uri>()
        val session = ModelImportSession(scope) { _, documents ->
            received = documents
            ModelImports.Result(2, null, setOf(ModelPack.Search))
        }
        try {
            activity.setContent {
                LumenTheme { OfflineModelImportRow(session) }
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
            assertEquals(selected, received)
            compose.onNodeWithText(message, substring = true).assertIsDisplayed()
            val ready = activity.getString(R.string.smart_import_ready, activity.getString(R.string.smart_pack_search))
            compose.onNodeWithText(ready, substring = true).assertIsDisplayed()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun emptyAndCancelledPickerResultsNeverStartAnImport() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var calls = 0
        val session = ModelImportSession(scope) { _, _ -> calls++; null }
        try {
            activity.setContent { LumenTheme { OfflineModelImportRow(session) } }
            val shadow = shadowOf(activity)
            for (code in listOf(Activity.RESULT_CANCELED, Activity.RESULT_OK)) {
                compose.onNodeWithText(activity.getString(R.string.smart_import_files)).performClick()
                shadow.receiveResult(shadow.nextStartedActivityForResult.intent, code, Intent())
                compose.waitForIdle()
                assertEquals(0, calls)
                assertEquals(ModelImportSession.State.Idle, session.state.value)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun importSurvivesLeavingThePageAndActivityRecreationWithoutStartingTwice() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val visible = mutableStateOf(true)
        val gate = CompletableDeferred<ModelImports.Result>()
        var calls = 0
        val session = ModelImportSession(scope) { _, _ ->
            calls++
            gate.await()
        }
        fun mount(activity: ComponentActivity) {
            activity.setContent { LumenTheme { if (visible.value) OfflineModelImportRow(session) } }
        }
        try {
            val activity = controller.get()
            mount(activity)
            compose.onNodeWithText(activity.getString(R.string.smart_import_files)).performClick()
            val shadow = shadowOf(activity)
            shadow.receiveResult(shadow.nextStartedActivityForResult.intent, Activity.RESULT_OK, Intent().setData(zip))
            val running = session.state.value as ModelImportSession.State.Importing
            assertEquals(1, running.task.selectedCount)
            compose.onNodeWithText(activity.getString(R.string.smart_import_working)).assertHasNoClickAction()
            session.start(activity, listOf(zip))
            compose.runOnIdle { visible.value = false }
            compose.onNodeWithText(activity.getString(R.string.smart_import_files)).assertDoesNotExist()
            assertEquals(running, session.state.value)

            compose.runOnIdle { visible.value = true }
            compose.onNodeWithText(activity.getString(R.string.smart_import_working)).assertIsDisplayed()
            controller.recreate()
            val recreated = controller.get()
            mount(recreated)
            compose.onNodeWithText(recreated.getString(R.string.smart_import_working)).assertIsDisplayed()
            assertEquals(running, session.state.value)
            assertEquals(1, calls)

            gate.complete(ModelImports.Result(1, null, emptySet()))
            val incomplete = recreated.getString(R.string.smart_import_incomplete)
            assertMessage(incomplete)
            val ready = recreated.getString(R.string.smart_import_ready, "").trimEnd()
            compose.onNodeWithText(ready, substring = true).assertDoesNotExist()
            compose.onNodeWithText(recreated.getString(android.R.string.ok)).performClick()
            compose.onNodeWithText(recreated.getString(R.string.smart_import_files)).performClick()
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, shadowOf(recreated).nextStartedActivityForResult.intent.action)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun busyAndFailuresShowSpecificResultsAndReleaseTheSlot() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val attempts = listOf<Pair<Int, () -> ModelImports.Result?>>(
            R.string.smart_import_busy to { null },
            R.string.smart_import_io to { throw IOException() },
            R.string.smart_import_io to { throw FileNotFoundException() },
            R.string.smart_import_io to { throw SecurityException() },
            R.string.smart_import_io to { throw IllegalArgumentException() },
            R.string.smart_import_cancelled to { throw CancellationException() },
            R.string.smart_import_checksum to {
                ModelImports.Result(0, ModelImports.Rejection("model.onnx", ModelImports.Error.ChecksumMismatch), emptySet())
            },
            R.string.smart_import_rollback to {
                ModelImports.Result(0, ModelImports.Rejection("models.zip", ModelImports.Error.RollbackFailed), emptySet())
            },
            R.string.smart_import_activation_failed to {
                ModelImports.Result(1, null, emptySet(), activationFailed = true)
            },
        ).iterator()
        var expected = 0
        val session = ModelImportSession(scope) { _, _ ->
            val (message, result) = attempts.next()
            expected = message
            result()
        }
        try {
            activity.setContent { LumenTheme { OfflineModelImportRow(session) } }
            val shadow = shadowOf(activity)
            while (attempts.hasNext()) {
                compose.onNodeWithText(activity.getString(R.string.smart_import_files)).performClick()
                val picker = shadow.nextStartedActivityForResult.intent
                shadow.receiveResult(picker, Activity.RESULT_OK, Intent().setData(zip))
                assertMessage(activity.getString(expected))
                if (expected == R.string.smart_import_rollback) {
                    compose.onNodeWithText(activity.getString(R.string.smart_import_failed), substring = true)
                        .assertDoesNotExist()
                }
                compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
                assertEquals(ModelImportSession.State.Idle, session.state.value)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun assertMessage(message: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(message, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(message, substring = true).assertIsDisplayed()
    }
}
