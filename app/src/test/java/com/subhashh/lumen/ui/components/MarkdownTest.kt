package com.subhashh.lumen.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test
    fun releaseChanges_stopsAtTheDownloadTable() {
        val notes = "## New\n\n- **A thing.** Text.\n\n## Which file to download\n\n| File | For |\n|---|---|"
        assertEquals("## New\n\n- **A thing.** Text.", releaseChanges(notes))
    }

    @Test
    fun releaseChanges_keepsNotesWithoutTheTable() {
        assertEquals("## Fixed\n\n- One bug.", releaseChanges("## Fixed\n\n- One bug.\n"))
    }
}
