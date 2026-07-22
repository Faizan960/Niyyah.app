package com.salahlock.app.ui.masjid

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.model.PrayerSource
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.MutedSage
import com.salahlock.app.work.AlarmRefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── State + ViewModel (used by the standalone Settings screen) ──────────────────

data class PrayerSourceUiState(
    val source: PrayerSource = PrayerSource.API,
    val cityName: String = "",
    val savedMasjidName: String = "",
    val masjidConfigured: Boolean = false,
)

class PrayerSourceViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val repo = app.prayerSourceRepository

    private val _state = MutableStateFlow(PrayerSourceUiState())
    val state: StateFlow<PrayerSourceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.getPrayerSource(),
                app.userPreferences.cityName,
                repo.getLocalMasjid(),
            ) { source, city, masjid ->
                PrayerSourceUiState(
                    source = source,
                    cityName = city,
                    savedMasjidName = masjid?.masjidName ?: "",
                    masjidConfigured = !masjid?.masjidName.isNullOrBlank(),
                )
            }.collect { newState -> _state.update { newState } }
        }
    }

    fun setSource(source: PrayerSource) {
        viewModelScope.launch {
            repo.setPrayerSource(source)
            // Keep Home + Salah Lock scheduling on the same effective schedule.
            AlarmRefreshWorker.runNow(app)
        }
    }
}

// ── Shared selector body (reused by the Home bottom sheet + Settings screen) ─────

/**
 * BM-HOME-PRAYER-UX — the "Prayer Times" source chooser.
 *
 * One authoritative preference: GPS / Current Location vs Local Masjid. A saved masjid
 * is never deleted by switching to GPS; switching back restores it without reconfig.
 */
@Composable
fun PrayerSourceOptions(
    source: PrayerSource,
    cityName: String,
    savedMasjidName: String,
    masjidConfigured: Boolean,
    onSelectGps: () -> Unit,
    onSelectMasjid: () -> Unit,
    onConfigureMasjid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "PRAYER TIMES",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        SourceRow(
            icon = Icons.Outlined.LocationOn,
            title = "GPS / Current Location",
            subtitle = cityName.ifBlank { "Calculated from your location" },
            selected = source == PrayerSource.API,
            onClick = onSelectGps,
        )
        Spacer(Modifier.height(12.dp))
        SourceRow(
            icon = Icons.Outlined.Mosque,
            title = "Local Masjid",
            subtitle = if (masjidConfigured) savedMasjidName else "Not configured yet",
            selected = source == PrayerSource.LOCAL_MASJID,
            // Selecting Local Masjid only makes sense once one is configured — otherwise
            // route straight to setup rather than activating an empty timetable.
            onClick = { if (masjidConfigured) onSelectMasjid() else onConfigureMasjid() },
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onConfigureMasjid)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.EditLocationAlt,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (masjidConfigured) "Change / edit masjid" else "Configure local masjid",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = EmeraldPrimary,
            )
        }
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) EmeraldPrimary else MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) EmeraldPrimary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface
            )
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EmeraldPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = EmeraldPrimary),
        )
    }
}

// ── Bottom sheet host (Home) ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSourceBottomSheet(
    source: PrayerSource,
    cityName: String,
    savedMasjidName: String,
    masjidConfigured: Boolean,
    onSelectSource: (PrayerSource) -> Unit,
    onConfigureMasjid: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        PrayerSourceOptions(
            source = source,
            cityName = cityName,
            savedMasjidName = savedMasjidName,
            masjidConfigured = masjidConfigured,
            onSelectGps = { onSelectSource(PrayerSource.API); onDismiss() },
            onSelectMasjid = { onSelectSource(PrayerSource.LOCAL_MASJID); onDismiss() },
            onConfigureMasjid = { onDismiss(); onConfigureMasjid() },
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}

// ── Standalone Settings screen (Profile → App Settings entry) ───────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSourceSettingsScreen(
    onBack: () -> Unit,
    onConfigureMasjid: () -> Unit,
    viewModel: PrayerSourceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prayer Time Source", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Choose where Niyyah gets prayer times. Your saved masjid is kept even when GPS is selected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            PrayerSourceOptions(
                source = state.source,
                cityName = state.cityName,
                savedMasjidName = state.savedMasjidName,
                masjidConfigured = state.masjidConfigured,
                onSelectGps = { viewModel.setSource(PrayerSource.API) },
                onSelectMasjid = { viewModel.setSource(PrayerSource.LOCAL_MASJID) },
                onConfigureMasjid = onConfigureMasjid,
            )
        }
    }
}
