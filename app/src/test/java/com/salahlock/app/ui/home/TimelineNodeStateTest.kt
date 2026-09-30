package com.salahlock.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * fix/prayer-timeline-state-ui — locks the Home timeline's node-state priority so a
 * CURRENT-but-UNVERIFIED prayer can never render as the solid "completed" node again.
 *
 * The UI bug: the current prayer was drawn as a solid filled emerald circle — identical
 * to a verified prayer minus the check — making "current" indistinguishable from "done".
 * [timelineNodeState] now maps to an explicit [TimelineNodeState]; these tests pin the
 * two combinations the spec calls out plus the ordering guarantees.
 */
class TimelineNodeStateTest {

    @Test
    fun currentAndUnverified_isCurrentUnverified_notCompleted() {
        val state = timelineNodeState(completed = false, isCurrent = true, pastUnverified = false)
        assertEquals(TimelineNodeState.CURRENT_UNVERIFIED, state)
        // The whole point of the fix: current-unverified must NOT be the completed visual.
        assert(state != TimelineNodeState.COMPLETED)
    }

    @Test
    fun currentAndCompleted_completedWins() {
        // Completion is the more important semantic state — it wins even while current.
        assertEquals(
            TimelineNodeState.COMPLETED,
            timelineNodeState(completed = true, isCurrent = true, pastUnverified = false),
        )
    }

    @Test
    fun completedOnly_isCompleted() {
        assertEquals(
            TimelineNodeState.COMPLETED,
            timelineNodeState(completed = true, isCurrent = false, pastUnverified = false),
        )
    }

    @Test
    fun pastUnverified_isPastUnverified() {
        assertEquals(
            TimelineNodeState.PAST_UNVERIFIED,
            timelineNodeState(completed = false, isCurrent = false, pastUnverified = true),
        )
    }

    @Test
    fun completedBeatsPastUnverified() {
        // A verified prayer whose window has passed is completed, not "missed".
        assertEquals(
            TimelineNodeState.COMPLETED,
            timelineNodeState(completed = true, isCurrent = false, pastUnverified = true),
        )
    }

    @Test
    fun nothingSet_isUpcoming() {
        assertEquals(
            TimelineNodeState.UPCOMING,
            timelineNodeState(completed = false, isCurrent = false, pastUnverified = false),
        )
    }
}
