package com.salahlock.app.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahlock.app.R
import com.salahlock.app.auth.GoogleAuthClient
import com.salahlock.app.auth.GoogleAuthConfig
import com.salahlock.app.auth.GoogleAuthResult
import com.salahlock.app.ui.components.ProfileAvatar
import com.salahlock.app.ui.theme.NiyyahColors
import com.salahlock.app.ui.theme.NiyyahType
import kotlinx.coroutines.launch

/**
 * Profile — Figma frame 1:613 (light).
 *
 * BM-006.7: identity, statistics, milestones and the reflection preview are
 * live from [ProfileViewModel] (prayer records, Quran progress, bookmarks,
 * collections, achievements, spiritual rank, monthly report).
 */
private val TextBody = Color(0xFF45474E)
private val CardRadius = RoundedCornerShape(12.dp)
private val ChipFill = Color(0xFFF2F0EC)
private val CircleFill = Color(0xFFF6F3F2)
private val RingGold = Color(0xFFEEC064)
private val MintFill = Color(0x338BF7CB)
private val GoldFill = Color(0x33EEC064)

@Composable
fun ProfileScreen(
    onOpenCollections: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenReflection: () -> Unit = {},
    viewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authClient = remember(context) { GoogleAuthClient(context) }

    fun startGoogleSignIn() {
        if (!GoogleAuthConfig.isConfigured) {
            viewModel.startSignIn() // surfaces NOT_CONFIGURED
            return
        }
        viewModel.startSignIn() // LOADING
        scope.launch {
            when (val result = authClient.signIn()) {
                is GoogleAuthResult.Success -> viewModel.onSignInSuccess(
                    displayName = result.identity.displayName,
                    email = result.identity.email,
                    photoUrl = result.identity.photoUrl,
                    googleId = result.identity.googleId,
                )
                GoogleAuthResult.Cancelled -> viewModel.clearSignInError()
                GoogleAuthResult.NoCredential ->
                    viewModel.onSignInError("No Google account found on this device.")
                is GoogleAuthResult.Error -> viewModel.onSignInError(result.message)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ProfileHeader(onOpenSettings)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            IdentitySection(uiState, onSignIn = ::startGoogleSignIn)
            StatisticsCard(uiState)
            MilestonesSection(uiState, onOpenReflection)
            ReflectionPreviewCard(uiState, onOpenReflection)
            QuickLinks(uiState, onOpenCollections, onOpenBookmarks)
        }
    }
}

/** Header — node 1:648. Hamburger / NIYYAH / settings gear. */
@Composable
private fun ProfileHeader(onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(NiyyahColors.HeaderBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_menu),
                contentDescription = "Menu",
                tint = TextBody,
                modifier = Modifier.width(18.dp).height(12.dp),
            )
            Text(text = "NIYYAH", style = NiyyahType.Wordmark, color = NiyyahColors.TextPrimary)
            Icon(
                painter = painterResource(R.drawable.ic_settings_outline),
                contentDescription = "Settings",
                tint = TextBody,
                modifier = Modifier.size(20.dp).clickable { onOpenSettings() },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NiyyahColors.Hairline),
        )
    }
}

/**
 * Avatar + name — node 1:659. Fully live (BM-008.1): real Google photo/name/email
 * when signed in; when signed out the avatar becomes the Google sign-in trigger
 * (the design's Settings account row points here) with loading and error states.
 */
@Composable
private fun IdentitySection(uiState: ProfileUiState, onSignIn: () -> Unit) {
    val isLoading = uiState.signInState == SignInState.LOADING
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .border(1.dp, NiyyahColors.Hairline, CircleShape)
                .padding(1.dp)
                .clip(CircleShape)
                .then(if (uiState.isSignedIn || isLoading) Modifier else Modifier.clickable { onSignIn() }),
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(
                photoUrl = uiState.userPhotoUrl,
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
            )
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = uiState.userName.ifBlank { "As-salamu alaykum" },
                style = NiyyahType.Quote.copy(fontSize = 28.sp, lineHeight = 36.sp),
                color = NiyyahColors.TextPrimary,
            )
            Text(
                text = when {
                    uiState.isSignedIn && uiState.userEmail.isNotBlank() -> uiState.userEmail
                    uiState.signInState == SignInState.ERROR ->
                        uiState.signInError ?: "Sign-in failed. Tap your photo to try again."
                    uiState.signInState == SignInState.NOT_CONFIGURED ->
                        "Google sign-in is not configured."
                    else -> "Tap your photo to sign in with Google"
                },
                style = NiyyahType.Body,
                color = if (uiState.signInState == SignInState.ERROR) Color(0xFFE5484D) else TextBody,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOfNotNull(
                uiState.memberSince.ifBlank { null },
                uiState.spiritualRank.ifBlank { null },
            ).forEach { label ->
                Box(
                    modifier = Modifier
                        .background(ChipFill, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(text = label, style = NiyyahType.Badge, color = Color(0xFF666666))
                }
            }
        }
    }
}

/** Personal statistics — node 1:673. Live weekly consistency + Quran progress. */
@Composable
private fun StatisticsCard(uiState: ProfileUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, CardRadius)
            .border(1.dp, NiyyahColors.Border, CardRadius)
            .padding(25.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Personal Statistics",
                style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                color = NiyyahColors.TextPrimary,
                modifier = Modifier.padding(bottom = 9.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NiyyahColors.Border),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatColumn(
                label = "PRAYER\nCONSISTENCY",
                bigText = "${uiState.weeklyConsistencyPercent}%",
                sideText = "this week",
                progress = uiState.weeklyConsistencyPercent / 100f,
                barColor = NiyyahColors.Navy,
                modifier = Modifier.weight(1f),
            )
            StatColumn(
                label = "READING\nPROGRESS",
                bigText = if (uiState.readingJuz > 0) "Juz\n${uiState.readingJuz}" else "—",
                sideText = uiState.readingSurahName.ifBlank { "Begin reading" },
                progress = uiState.readingProgressFraction,
                barColor = RingGold,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    bigText: String,
    sideText: String,
    progress: Float,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = NiyyahType.LabelUppercase.copy(letterSpacing = 1.4.sp),
            color = TextBody,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = bigText,
                style = NiyyahType.Quote.copy(fontSize = 36.sp, lineHeight = 36.sp, letterSpacing = (-0.36).sp),
                color = NiyyahColors.TextPrimary,
            )
            Text(
                text = sideText,
                style = NiyyahType.Body,
                color = TextBody,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(CircleFill),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
        }
    }
}

/** Milestones bento — node 1:699. Tiles show real unlocked achievements. */
@Composable
private fun MilestonesSection(uiState: ProfileUiState, onOpenReflection: () -> Unit) {
    val achievements = uiState.unlockedAchievements
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Milestones",
            style = NiyyahType.Quote.copy(lineHeight = 32.sp),
            color = NiyyahColors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MilestoneTile(
                circleColor = MintFill,
                iconRes = R.drawable.ic_streak_calendar,
                title = achievements.getOrNull(0)?.title ?: "First Steps",
                subtitle = achievements.getOrNull(0)?.description ?: "Your journey begins",
                modifier = Modifier.weight(1f),
            )
            MilestoneTile(
                circleColor = GoldFill,
                iconRes = R.drawable.ic_khatam_book,
                title = achievements.getOrNull(1)?.title ?: "In Progress",
                subtitle = achievements.getOrNull(1)?.description ?: "Keep your rhythm",
                modifier = Modifier.weight(1f),
            )
        }
        // Set-new-goal dashed tile — node 1:721.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(342.dp)
                .clickable { onOpenReflection() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color(0xFFE7E2DA),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)),
                    ),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_plus_small),
                    contentDescription = null,
                    tint = TextBody,
                    modifier = Modifier.size(14.dp),
                )
                Text(text = "Set new goal", style = NiyyahType.Badge, color = TextBody)
            }
        }
    }
}

@Composable
private fun MilestoneTile(
    circleColor: Color,
    iconRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(6.dp, CardRadius, ambientColor = Color(0x0D1E2D4C), spotColor = Color(0x0D1E2D4C))
            .background(NiyyahColors.SurfaceElevated, CardRadius)
            .padding(horizontal = 16.dp, vertical = 27.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(circleColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier.width(22.dp).height(21.dp),
            )
        }
        Text(
            text = title,
            style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
            color = NiyyahColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(text = subtitle, style = NiyyahType.Badge, color = TextBody, textAlign = TextAlign.Center)
    }
}

/** Monthly reflection preview — node 1:726. Live from the current report. */
@Composable
private fun ReflectionPreviewCard(uiState: ProfileUiState, onOpenReflection: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardRadius)
            .background(NiyyahColors.Surface)
            .border(1.dp, NiyyahColors.Border, CardRadius),
    ) {
        Image(
            painter = painterResource(R.drawable.img_reflection_preview),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(128.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = uiState.reflectionHeadline.ifBlank { "Your Monthly Reflection" },
                    style = NiyyahType.Quote.copy(lineHeight = 32.sp),
                    color = NiyyahColors.TextPrimary,
                )
                Text(
                    text = uiState.reflectionBody.ifBlank {
                        "A gentle look back at your month of worship awaits."
                    }.let { if (it.length > 96) it.take(96).trimEnd() + "…" else it },
                    style = NiyyahType.Body,
                    color = TextBody,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(modifier = Modifier.clickable { onOpenReflection() }) {
                    Text(
                        text = "READ FULL ENTRY",
                        style = NiyyahType.LabelUppercase.copy(letterSpacing = 1.4.sp),
                        color = NiyyahColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(136.dp)
                            .height(1.dp)
                            .background(NiyyahColors.TextPrimary),
                    )
                }
            }
            // Date badge — node 1:735.
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp)
                    .offset(y = (-32).dp)
                    .size(64.dp)
                    .shadow(8.dp, CircleShape)
                    .background(NiyyahColors.Navy, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = uiState.reflectionMonthLabel.ifBlank { "—" },
                    style = NiyyahType.Badge.copy(letterSpacing = 1.2.sp, lineHeight = 12.sp),
                    color = Color.White,
                )
                Text(
                    text = uiState.reflectionDayLabel,
                    style = NiyyahType.Quote.copy(lineHeight = 24.sp),
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Quick links — node 1:742. Live counts; navigate to Collections / Bookmarks. */
@Composable
private fun QuickLinks(
    uiState: ProfileUiState,
    onOpenCollections: () -> Unit,
    onOpenBookmarks: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        QuickLinkRow(
            iconRes = R.drawable.ic_quicklink_collections,
            title = "My Collections",
            subtitle = "${uiState.collectionsCount} collections curated",
            onClick = onOpenCollections,
        )
        QuickLinkRow(
            iconRes = R.drawable.ic_bookmark_outline,
            title = "Bookmarks",
            subtitle = "${uiState.bookmarksCount} saved items",
            onClick = onOpenBookmarks,
        )
    }
}

@Composable
private fun QuickLinkRow(iconRes: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NiyyahColors.Surface, CardRadius)
            .border(1.dp, NiyyahColors.Border, CardRadius)
            .clickable { onClick() }
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(CircleFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = NiyyahColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = NiyyahType.LabelUppercase.copy(letterSpacing = 0.7.sp),
                color = NiyyahColors.TextPrimary,
            )
            Text(text = subtitle, style = NiyyahType.Badge, color = TextBody)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = TextBody,
            modifier = Modifier.width(8.dp).height(12.dp),
        )
    }
}
