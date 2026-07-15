package com.salahlock.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.salahlock.app.R
import com.salahlock.app.ui.theme.NiyyahColors

/**
 * The single source of truth for rendering a user's avatar (BM-008.1). When the
 * user is signed in with Google, [photoUrl] loads their real profile photo via
 * Coil; when signed out it falls back to a neutral silhouette — never a dummy
 * stock photo. Callers own the size / clip / border; this fills the given box.
 */
@Composable
fun ProfileAvatar(
    photoUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (photoUrl.isNullOrBlank()) {
        Box(
            modifier = modifier.background(NiyyahColors.SoftFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_profile),
                contentDescription = contentDescription,
                tint = NiyyahColors.TextSecondary,
                modifier = Modifier.fillMaxSize(0.5f),
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photoUrl)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
