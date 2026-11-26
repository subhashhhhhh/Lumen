package com.subhashh.lumen.data

import android.content.Context
import com.subhashh.lumen.ai.AiModel
import com.subhashh.lumen.ai.LumenAiEngine
import com.subhashh.lumen.ai.ModelCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.RandomAccessFile

/**
 * Settings → Storage. Mostly its one destructive step, removing what an old search model left
 * behind: it must never take the model being used, nor the one being downloaded to replace it.
 * Model files are made sparse, so their full sizes cost no disk.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(LumenAiEngine::class)
class StorageUsageTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs = LumenPrefs(context)
    private val accurate = ModelCatalog.all.first { it.id == "siglip2-b16" }
    private val fast = ModelCatalog.all.first { it.id == "clip-vit-b32" }
    private val search get() = File(context.noBackupFilesDir, "search")

    @Before
    fun clean() {
        context.noBackupFilesDir.deleteRecursively()
        context.noBackupFilesDir.mkdirs()
        search.mkdirs()
        prefs.searchModel = null
    }

    private fun sparse(file: File, bytes: Long) {
        file.parentFile!!.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(bytes) }
    }

    /** Every file of [model], full size: installed. */
    private fun install(model: AiModel) {
        for (f in model.files) sparse(File(model.dir(context), f.name), f.bytes)
        assertTrue("${model.id} should count as installed", model.installed(context))
    }

    /** Part of [model]'s first file: a download under way. */
    private fun downloading(model: AiModel): File {
        val f = model.files.first()
        val file = File(model.dir(context), f.name)
        sparse(file, f.bytes / 3)
        assertFalse(model.installed(context))
        return file
    }

    /** The index files Lumen AI keeps per search model, as Storage recognises them. */
    private fun indexOf(model: AiModel): List<File> = listOf(
        File(search, "${model.id}.db"),
        File(search, "suggestions-${model.id}.bin"),
        File(search, "cutout-prompts-${model.id}.bin"),
    ).onEach { sparse(it, 1000) }

    private fun others(): List<File> = listOf(
        File(search, "text.db"),
        File(search, "faces.db"),
        File(context.noBackupFilesDir, "models/faces/edgeface_s.onnx"),
        File(context.noBackupFilesDir, "models/cutout/cutout.tflite"),
        File(context.noBackupFilesDir, "models/ppocrv6-small/det.onnx"),
    ).onEach { sparse(it, 5000) }

    // --- Removing an old search model ---

    @Test
    fun anOldModelAndItsIndexGoAndNothingElseDoes() = runBlocking {
        prefs.searchModel = fast.id
        install(fast)
        install(accurate)
        val fastIndex = indexOf(fast)
        val oldIndex = indexOf(accurate)
        val kept = others()

        StorageUsage.removeOldSearch(context)

        assertFalse(accurate.dir(context).exists())
        for (f in oldIndex) assertFalse("${f.name} should be gone", f.exists())
        assertTrue(fast.installed(context))
        for (f in fastIndex + kept) assertTrue("${f.name} must stay", f.exists())
    }

    @Test
    fun theModelBeingDownloadedIsNeverRemoved() = runBlocking {
        // Switching Accurate → Fast: Fast is chosen and downloading, Accurate still answers searches
        // until it's done. installed() then reports Accurate, so "old" must not mean "not
        // installed()": that would delete the download (and the index Fast is building) mid-way.
        prefs.searchModel = fast.id
        install(accurate)
        val partial = downloading(fast)
        val fastIndex = indexOf(fast)
        indexOf(accurate)

        StorageUsage.removeOldSearch(context)

        assertTrue("the download under way was deleted", partial.exists())
        for (f in fastIndex) assertTrue("${f.name}, the new model's index, was deleted", f.exists())
        assertTrue("the model still answering searches was deleted", accurate.installed(context))
    }

    @Test
    fun aSwitchUnderWayIsntShownAsSomethingToRemove() = runBlocking {
        prefs.searchModel = fast.id
        install(accurate)
        downloading(fast)
        indexOf(fast)
        val usage = StorageUsage.measure(context)
        assertEquals("a download under way offered as left over", 0L, usage.oldSearch)
    }

    @Test
    fun withNoSearchModelNothingIsRemoved() = runBlocking {
        val index = indexOf(accurate) + indexOf(fast)
        val kept = others()
        StorageUsage.removeOldSearch(context)
        for (f in index + kept) assertTrue("${f.name} must stay", f.exists())
    }

    // --- Measuring ---

    @Test
    fun theLeftOverIsTheOldModelAndItsIndex() = runBlocking {
        prefs.searchModel = fast.id
        install(fast)
        install(accurate)
        indexOf(fast)
        indexOf(accurate)
        val usage = StorageUsage.measure(context)
        assertEquals(accurate.files.sumOf { it.bytes } + 3 * 1000L, usage.oldSearch)
    }

    @Test
    fun nothingIsCountedTwice() = runBlocking {
        prefs.searchModel = fast.id
        install(fast)
        install(accurate)
        indexOf(fast)
        indexOf(accurate)
        others()
        val usage = StorageUsage.measure(context)
        val byType = usage.kinds.associate { it.type to it.bytes }
        // A kind with parts is exactly its parts (only empty parts are left off the list).
        for (kind in usage.kinds) if (kind.parts.isNotEmpty()) assertEquals("${kind.type}'s parts", kind.bytes, kind.parts.sumOf { it.bytes })
        // Everything made here is in Lumen's own folders: Models + Indexes + Caches + Other is what
        // they take, no more (a part counted in two kinds would push it over).
        val onDisk = context.dataDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } +
            (context.getExternalFilesDir(null)?.parentFile?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L)
        val counted = byType.getValue(StorageUsage.Type.Models) + byType.getValue(StorageUsage.Type.Indexes) +
            byType.getValue(StorageUsage.Type.Caches) + byType.getValue(StorageUsage.Type.Other)
        assertEquals(onDisk, counted)
        assertEquals(usage.kinds.sumOf { it.bytes }, usage.total)
    }

    // --- Thumbnails ---

    @Test
    fun oneThumbnailCacheForTheWholeApp() {
        // Storage's Clear goes through the same cache the grid writes to, or the grid's stays open on deleted files.
        assertSame(GridThumbnail.Factory.disk(context), GridThumbnail.Factory.disk(context.applicationContext))
    }
}
