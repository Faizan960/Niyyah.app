package com.salahlock.app.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Single source of truth for all motion in SalahLock.
 *
 * Motion philosophy (Apple Books / VisionOS / Kindle / premium journals):
 *   calm · intentional · lightweight · spiritual · focused.
 *
 * Rules enforced here:
 *   - No animation exceeds [Max] (400ms).
 *   - Preferred duration is [Normal] (250ms).
 *   - No bouncy springs — only critically-damped (NoBouncy) springs are exposed.
 *   - Reading content is never animated (no token here drives reading text).
 *
 * Every animation in the app must consume a token from this file.
 * Do not hardcode durations or springs in screens.
 */
object MotionTokens {

    // ── Durations (milliseconds) ──────────────────────────────────────────────
    const val Fast = 150      // micro-interactions: bookmark pop, press feedback
    const val Normal = 250    // default: navigation, crossfades, fades
    const val Slow = 300      // progress fills, theme transition, card flip
    const val Max = 400       // hard ceiling — never exceed

    // ── Easing ────────────────────────────────────────────────────────────────
    /** Standard entering/moving easing — content settling into place. */
    val StandardEasing: Easing = FastOutSlowInEasing

    /** Decelerate easing — elements entering the screen. */
    val DecelerateEasing: Easing = LinearOutSlowInEasing

    /** Gentle emphasis curve for micro-interactions (no overshoot beyond intent). */
    val EmphasisEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // ── Springs (critically damped — never bouncy) ────────────────────────────
    /** Position/size spring for indicators and selection movement. */
    fun <T> noBounceSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Slightly snappier spring for responsive selection (still no bounce). */
    fun <T> mediumSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    // ── Common tween specs ────────────────────────────────────────────────────
    fun <T> normalTween() = tween<T>(durationMillis = Normal, easing = StandardEasing)
    fun <T> slowTween() = tween<T>(durationMillis = Slow, easing = StandardEasing)
    fun <T> fastTween() = tween<T>(durationMillis = Fast, easing = EmphasisEasing)

    // ── Stagger ───────────────────────────────────────────────────────────────
    /** Per-item stagger delay for list entrances. */
    const val StaggerStepMs = 40
    /** Maximum cumulative stagger so long lists never feel slow. */
    const val StaggerMaxMs = 200

    fun staggerDelayFor(index: Int): Int = (index * StaggerStepMs).coerceAtMost(StaggerMaxMs)
}
