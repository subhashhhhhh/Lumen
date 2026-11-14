package com.subhashh.lumen.data.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEditTest {

    private val duration = 12_000L

    @Test
    fun anUntouchedEditKeepsTheWholeVideo() {
        val edit = VideoEdit()
        assertTrue(edit.isUnchanged)
        assertEquals(duration, edit.end(duration))
    }

    @Test
    fun theStartStopsShortOfTheEnd() {
        assertEquals(3_000L, VideoEdit().withStart(3_000, duration).startMs)
        assertEquals(0L, VideoEdit().withStart(-500, duration).startMs)
        assertEquals(duration - VideoEdit.MinLengthMs, VideoEdit().withStart(99_000, duration).startMs)
        assertEquals(5_500L, VideoEdit(endMs = 6_000).withStart(7_000, duration).startMs)
    }

    @Test
    fun theEndStopsShortOfTheStart() {
        assertEquals(8_000L, VideoEdit().withEnd(8_000, duration).endMs)
        assertEquals(4_500L, VideoEdit(startMs = 4_000).withEnd(1_000, duration).endMs)
    }

    @Test
    fun anEndDraggedBackToTheLastFrameIsNoTrim() {
        val edit = VideoEdit().withEnd(8_000, duration).withEnd(50_000, duration)
        assertNull(edit.endMs)
        assertTrue(edit.isUnchanged)
    }

    @Test
    fun aVideoShorterThanTheShortestClipStaysWhole() {
        val edit = VideoEdit().withStart(200, 300).withEnd(100, 300)
        assertEquals(0L, edit.startMs)
        assertNull(edit.endMs)
    }

    @Test
    fun fourLeftTurnsComeBackUpright() {
        var edit = VideoEdit()
        edit = edit.rotatedLeft()
        assertEquals(3, edit.quarterTurns)
        repeat(3) { edit = edit.rotatedLeft() }
        assertEquals(0, edit.quarterTurns)
        assertTrue(edit.isUnchanged)
    }
}
