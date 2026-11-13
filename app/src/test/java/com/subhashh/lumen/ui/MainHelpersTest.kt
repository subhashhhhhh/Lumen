package com.subhashh.lumen.ui

import android.content.Context
import com.subhashh.lumen.ai.Person
import com.subhashh.lumen.data.FileResult
import com.subhashh.lumen.data.media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The pure helpers that moved to MainHelpers.kt: naming a person after someone else, and copy/move messages. */
@RunWith(RobolectricTestRunner::class)
class MainHelpersTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun person(id: Long, name: String?) = Person(
        id = id, name = name, hidden = false, mediaIds = setOf(id), faceCount = 1,
        coverMediaId = id, coverBox = listOf(0f, 0f, 1f, 1f), coverFaceId = id, coverInVideo = false,
    )

    private val chiara = person(1, "Chiara")
    private val unnamed = person(2, null)
    private val marco = person(3, "  Marco ")
    private val people = listOf(chiara to media(101), unnamed to media(102), marco to media(103))

    // --- mergeProposalFor ---

    @Test
    fun namingSomeoneAfterAnotherPersonProposesAMerge() {
        val proposal = mergeProposalFor(unnamed, "Chiara", people)
        assertNotNull(proposal)
        assertEquals(unnamed, proposal!!.person)
        assertEquals(102L, proposal.cover.id)
        assertEquals(chiara, proposal.match)
        assertEquals(101L, proposal.matchCover.id)
        assertEquals("Chiara", proposal.name)
    }

    @Test
    fun anyCaseAndSpacesStillMatch() {
        assertEquals(chiara, mergeProposalFor(unnamed, "  cHIARA ", people)?.match)
        // The other person's stored name has stray spaces; the proposal shows it trimmed.
        val proposal = mergeProposalFor(unnamed, "marco", people)
        assertEquals(marco, proposal?.match)
        assertEquals("Marco", proposal?.name)
    }

    @Test
    fun aFreeNameProposesNothing() {
        assertNull(mergeProposalFor(unnamed, "Giulia", people))
        assertNull(mergeProposalFor(unnamed, "Chiar", people))
        assertNull(mergeProposalFor(unnamed, "Chiara Rossi", people))
    }

    @Test
    fun blankNamesProposeNothing() {
        // An unnamed person must never match a blank name ("" equals "" after trimming null → no).
        assertNull(mergeProposalFor(chiara, "", people))
        assertNull(mergeProposalFor(chiara, "   ", people))
    }

    @Test
    fun keepingYourOwnNameIsNotAMerge() {
        assertNull(mergeProposalFor(chiara, "Chiara", people))
        assertNull(mergeProposalFor(chiara, "chiara", people))
    }

    @Test
    fun thePersonMustBeAmongThePeopleToGetTheirCover() {
        assertNull(mergeProposalFor(person(9, null), "Chiara", people))
    }

    @Test
    fun theFirstOfSeveralNamesakesIsProposed() {
        val twin = person(4, "chiara")
        val proposal = mergeProposalFor(unnamed, "Chiara", people + (twin to media(104)))
        assertEquals(chiara, proposal?.match)
    }

    // --- fileMessage ---

    private fun message(move: Boolean, done: Int, failed: Int, access: Boolean = false) =
        fileMessage(context, move, FileResult(done, failed, access), "Trip")

    @Test
    fun everythingWent() {
        assertEquals("Moved 1 item to Trip", message(move = true, done = 1, failed = 0))
        assertEquals("Copied 5 items to Trip", message(move = false, done = 5, failed = 0))
    }

    @Test
    fun nothingToDo() {
        assertEquals("Already in Trip", message(move = true, done = 0, failed = 0))
        assertEquals("Already in Trip", message(move = false, done = 0, failed = 0))
    }

    @Test
    fun everythingFailed() {
        assertEquals("Couldn't move to Trip", message(move = true, done = 0, failed = 3))
        assertEquals("Couldn't copy to Trip", message(move = false, done = 0, failed = 1))
    }

    @Test
    fun everythingNeededAccess() {
        assertEquals(
            "Couldn't move into Trip: that folder needs All files access (Settings → Permissions)",
            message(move = true, done = 0, failed = 2, access = true),
        )
    }

    @Test
    fun someFailed() {
        assertEquals("Moved 2 items to Trip; 1 failed", message(move = true, done = 2, failed = 1))
        assertEquals("Copied 1 item to Trip; 4 failed", message(move = false, done = 1, failed = 4))
    }

    /**
     * 8 of 10 photos moved and 2 needed All files access. The message says only "Couldn't move into
     * Trip", so it reads as if nothing moved: someone who then moves the 10 again from another
     * album, or copies them again, gets duplicates. A partial result must say how many went.
     */
    @Test
    fun partlyDoneWhileSomeNeededAccessStillSaysWhatMoved() {
        val text = message(move = true, done = 8, failed = 2, access = true)
        assertTrue(text, text.contains("8"))
        val copied = message(move = false, done = 8, failed = 2, access = true)
        assertTrue(copied, copied.contains("8"))
    }
}
