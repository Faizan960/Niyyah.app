package com.salahlock.app.ui.navigation

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.theme.MotionTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Tiny in-memory cache so the Google photo isn't re-fetched on every recomposition / page swipe. */
private val avatarBitmapCache = mutableMapOf<String, ImageBitmap>()

/**
 * Google-style account avatar used as the entry point to [ProfileScreen].
 *
 * Identity comes from the Clerk-backed single auth state (AuthRepository).
 * Content priority: Google profile photo → name initials → person icon.
 *
 * - Circular, [sizeDp] (default 40dp), Material 3 surface, subtle elevation + border.
 * - Respects dark / AMOLED via theme colors only.
 * - Animated press feedback via [MotionTokens] (Fast, no overshoot).
 * - 48dp touch target + content description for TalkBack.
 */
@Composable
fun ProfileAvatar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 40,
) {
    val context = LocalContext.current
    val app = context.applicationContext as SalahLockApplication
    // BM-AUTH-001: identity comes from the Clerk-backed single auth state.
    val authState by app.authRepository.state.collectAsState()
    val signedIn = authState as? com.salahlock.app.auth.AuthState.SignedIn
    val displayName = signedIn?.user?.name ?: ""
    val photoUrl = signedIn?.user?.imageUrl

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = MotionTokens.fastTween(),
        label = "avatar_press_scale",
    )

    // Async-load the Google photo off the main thread; falls back gracefully on any error.
    val photo by produceState<ImageBitmap?>(initialValue = photoUrl?.let { avatarBitmapCache[it] }, photoUrl) {
        val url = photoUrl
        if (url.isNullOrBlank()) { value = null; return@produceState }
        avatarBitmapCache[url]?.let { value = it; return@produceState }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000; readTimeout = 5000; doInput = true
                    instanceFollowRedirects = true
                }
                // Buffer fully before decoding: BitmapFactory.decodeStream can return
                // null on network streams (skip()/mark() quirks); decodeByteArray is robust.
                val bytes = conn.inputStream.use { it.readBytes() }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.onFailure { android.util.Log.w("ProfileAvatar", "profile photo load failed: ${it.message}") }.getOrNull()
        }
        if (loaded != null) { avatarBitmapCache[url] = loaded; value = loaded }
    }

    Box(
        modifier = modifier
            .size(48.dp) // accessibility touch target
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick,
            )
            .semantics { contentDescription = "Open profile" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(sizeDp.dp).scale(scale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            // SL-015: shadowElevation removed — the drop shadow was clipped by the
            // parent's 48dp circle clip, rendering as a vertical hairline artifact at
            // the top-right of Home on some GPUs. Tonal elevation + border carry the
            // depth cue without an off-shape shadow pass.
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                val bmp = photo
                when {
                    bmp != null -> Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    displayName.isNotBlank() -> Text(
                        text = initialsOf(displayName),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = (sizeDp * 0.4f).sp,
                    )
                    else -> Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size((sizeDp * 0.55f).dp),
                    )
                }
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
