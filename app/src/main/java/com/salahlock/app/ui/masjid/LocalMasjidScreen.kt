package com.salahlock.app.ui.masjid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahlock.app.SalahLockApplication
import com.salahlock.app.data.db.entity.LocalMasjidEntity
import com.salahlock.app.data.model.PrayerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahlock.app.theme.EmeraldPrimary
import com.salahlock.app.theme.MutedSage

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class LocalMasjidUiState(
    val masjidName: String = "",
    val fajr: String = "",
    val dhuhr: String = "",
    val asr: String = "",
    val maghrib: String = "",
    val isha: String = "",
    // SL-004 — Jumma (Friday). Optional; stored in DataStore, not Room (no migration).
    val jumma1: String = "",
    val jumma2: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
)

/** Validates "HH:mm" format (24-hour). */
private fun isValidTime(value: String): Boolean {
    if (!value.matches(Regex("\\d{1,2}:\\d{2}"))) return false
    val parts = value.split(":")
    val h = parts[0].toIntOrNull() ?: return false
    val m = parts[1].toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59
}

class LocalMasjidViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SalahLockApplication
    private val repo = app.prayerSourceRepository

    private val _state = MutableStateFlow(LocalMasjidUiState())
    val state: StateFlow<LocalMasjidUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = repo.getLocalMasjidOnce()
            val j1 = app.userPreferences.jumma1.first()
            val j2 = app.userPreferences.jumma2.first()
            _state.update {
                it.copy(
                    masjidName = existing?.masjidName ?: it.masjidName,
                    fajr = existing?.fajr ?: it.fajr,
                    dhuhr = existing?.dhuhr ?: it.dhuhr,
                    asr = existing?.asr ?: it.asr,
                    maghrib = existing?.maghrib ?: it.maghrib,
                    isha = existing?.isha ?: it.isha,
                    jumma1 = j1,
                    jumma2 = j2,
                )
            }
        }
    }

    fun update(field: String, value: String) = _state.update { s ->
        when (field) {
            "name"    -> s.copy(masjidName = value)
            "fajr"    -> s.copy(fajr = value)
            "dhuhr"   -> s.copy(dhuhr = value)
            "asr"     -> s.copy(asr = value)
            "maghrib" -> s.copy(maghrib = value)
            "isha"    -> s.copy(isha = value)
            "jumma1"  -> s.copy(jumma1 = value)
            "jumma2"  -> s.copy(jumma2 = value)
            else -> s
        }
    }

    fun save() {
        val s = _state.value
        val times = listOf(s.fajr, s.dhuhr, s.asr, s.maghrib, s.isha)
        val invalidTime = times.firstOrNull { it.isNotBlank() && !isValidTime(it) }

        if (s.masjidName.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter the masjid name.") }
            return
        }
        if (times.any { it.isBlank() }) {
            _state.update { it.copy(errorMessage = "Please enter all five prayer times.") }
            return
        }
        if (invalidTime != null) {
            _state.update { it.copy(errorMessage = "Invalid time format: \"$invalidTime\". Use HH:mm (e.g. 04:30).") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            repo.saveLocalMasjid(
                LocalMasjidEntity(
                    masjidName = s.masjidName.trim(),
                    fajr = s.fajr.trim(),
                    dhuhr = s.dhuhr.trim(),
                    asr = s.asr.trim(),
                    maghrib = s.maghrib.trim(),
                    isha = s.isha.trim(),
                    enabled = true,
                )
            )
            // SL-004 — Jumma persisted in DataStore alongside the Room timetable.
            app.userPreferences.setJummaTimes(s.jumma1, s.jumma2)
            repo.setPrayerSource(PrayerSource.LOCAL_MASJID)
            _state.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    /**
     * QoL: pre-fills the five prayer times from the current calculated/API times.
     * Reuses [PrayerTimesRepository.getTodayPrayers] — the user then tweaks only the
     * jamaat differences. No new repository or model.
     */
    fun importApiTimes() {
        viewModelScope.launch {
            val daily = app.prayerTimesRepository.getTodayPrayers().first() ?: run {
                _state.update { it.copy(errorMessage = "Prayer times unavailable — set your location first.") }
                return@launch
            }
            val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            _state.update {
                it.copy(
                    fajr = daily.fajr.format(fmt),
                    dhuhr = daily.dhuhr.format(fmt),
                    asr = daily.asr.format(fmt),
                    maghrib = daily.maghrib.format(fmt),
                    isha = daily.isha.format(fmt),
                )
            }
        }
    }
}

// ── Screen ─────────────────────────────────────────────────────────────────────

/**
 * Add / Edit local masjid prayer timetable.
 *
 * Fields: Masjid Name, Fajr, Dhuhr, Asr, Maghrib, Isha.
 * Times use free-text "HH:mm" input (24-hour). No Sunrise / Midnight / Imsak collected.
 *
 * Saving:
 *  - upserts [LocalMasjidEntity] (id = 1)
 *  - switches [PrayerSource] to LOCAL_MASJID
 *  - navigates back — prayer times update immediately via Flow
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMasjidScreen(
    onBack: () -> Unit,
    viewModel: LocalMasjidViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Auto-navigate back on save success
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Masjid Timings", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Enter your local masjid's prayer times. These will override the Aladhan API times.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Error message
            if (state.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = state.errorMessage!!,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // Masjid name
            OutlinedTextField(
                value = state.masjidName,
                onValueChange = { viewModel.update("name", it) },
                label = { Text("Masjid Name") },
                placeholder = { Text("e.g. Masjid Noor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = "PRAYER TIMES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSage,
                    letterSpacing = 1.5.sp,
                )
                // QoL — fill from current calculated/API times, then edit the differences
                Text(
                    text = "Import current times",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.importApiTimes() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldPrimary,
                )
            }

            // Which prayer's picker is open (null = none). Stored value is "HH:mm" 24h.
            var editingKey by remember { mutableStateOf<String?>(null) }

            val fields = listOf(
                Triple("Fajr",    state.fajr,    "fajr"),
                Triple("Dhuhr",   state.dhuhr,   "dhuhr"),
                Triple("Asr",     state.asr,     "asr"),
                Triple("Maghrib", state.maghrib, "maghrib"),
                Triple("Isha",    state.isha,    "isha"),
            )

            fields.forEach { (label, value, key) ->
                PrayerTimeRow(
                    label = label,
                    value = value,
                    onClick = { editingKey = key },
                )
            }

            // SL-004 — Jumma (Friday) section. Optional; Jumma 2 for masjids with two.
            Text(
                text = "JUMMA (FRIDAY)",
                style = MaterialTheme.typography.labelSmall,
                color = MutedSage,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            val jummaFields = listOf(
                Triple("Jumma 1", state.jumma1, "jumma1"),
                Triple("Jumma 2", state.jumma2, "jumma2"),
            )
            jummaFields.forEach { (label, value, key) ->
                PrayerTimeRow(
                    label = label,
                    value = value,
                    onClick = { editingKey = key },
                )
            }

            // Wheel time picker sheet (SL-011) — stores 24-hour "HH:mm".
            editingKey?.let { key ->
                val entry = (fields + jummaFields).first { it.third == key }
                TimePickerSheet(
                    label = entry.first,
                    initial = entry.second,
                    onConfirm = { h, m ->
                        viewModel.update(key, "%02d:%02d".format(h, m))
                        editingKey = null
                    },
                    onDismiss = { editingKey = null },
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !state.isSaving,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.White),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Save Masjid Timings", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

// ── Time picker row + dialog (Sprint M.2) ───────────────────────────────────────

/** Clickable card showing a prayer + its time (12-hour). Tapping opens the picker. */
@Composable
private fun PrayerTimeRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(prayerEmoji(label), fontSize = 22.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // SL-016 — jamaat emphasis caption
                Text(
                    text = "JAMAAT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSage,
                    letterSpacing = 1.2.sp,
                )
            }
            // Animate the displayed value when it changes
            Crossfade(targetState = value, label = "time_$label") { v ->
                Text(
                    text = if (v.isBlank()) "Set time" else displayTime(v),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (v.isBlank()) MutedSage else EmeraldPrimary,
                )
            }
        }
    }
}

/** Prayer-appropriate emoji for a label. */
private fun prayerEmoji(label: String): String = when (label) {
    "Jumma 1", "Jumma 2" -> "🕌"
    "Fajr" -> "🌅"
    "Dhuhr" -> "☀️"
    "Asr" -> "🌇"
    "Maghrib" -> "🌆"
    "Isha" -> "🌙"
    else -> "🕐"
}

/**
 * iOS-style wheel time picker in a bottom sheet (SL-011 — replaces the +/− steppers).
 * Three snapping wheels: hour (1–12), minute (00–59), AM/PM. Reuses the shared
 * [com.salahlock.app.ui.components.WheelPicker]. AMOLED-friendly (theme surfaces only).
 * Confirms a 24-hour (hour, minute); storage stays "HH:mm".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    label: String,
    initial: String,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val (init24, initMin) = parseHHmm(initial)
    var hourIndex by remember { mutableStateOf(((init24 + 11) % 12)) }      // 0..11 → hour 1..12
    var minuteIndex by remember { mutableStateOf(initMin) }                 // 0..59
    var amPmIndex by remember { mutableStateOf(if (init24 >= 12) 1 else 0) } // 0=AM 1=PM
    val sheetState = rememberModalBottomSheetState()

    val hours = remember { (1..12).map { "%02d".format(it) } }
    val minutes = remember { (0..59).map { "%02d".format(it) } }
    val amPm = remember { listOf("AM", "PM") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Set $label Time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.salahlock.app.ui.components.WheelPicker(
                    items = hours,
                    initialIndex = hourIndex,
                    onSelected = { hourIndex = it },
                )
                Text(":", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MutedSage)
                com.salahlock.app.ui.components.WheelPicker(
                    items = minutes,
                    initialIndex = minuteIndex,
                    onSelected = { minuteIndex = it },
                )
                Spacer(Modifier.width(8.dp))
                com.salahlock.app.ui.components.WheelPicker(
                    items = amPm,
                    initialIndex = amPmIndex,
                    onSelected = { amPmIndex = it },
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { onConfirm(to24Hour(hourIndex + 1, amPmIndex == 1), minuteIndex) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.White),
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** (hour 1..12, isPm) → 24-hour. 12 AM → 0, 12 PM → 12. */
private fun to24Hour(hour12: Int, isPm: Boolean): Int =
    if (isPm) (hour12 % 12) + 12 else (hour12 % 12)

/** "HH:mm" (24h) → "h:mm a" for display. Returns the raw value on parse failure. */
private fun displayTime(hhmm: String): String = runCatching {
    java.time.LocalTime.parse(hhmm).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(hhmm)

/** "HH:mm" → (hour, minute); defaults to 05:00 when blank/invalid. */
private fun parseHHmm(hhmm: String): Pair<Int, Int> = runCatching {
    val t = java.time.LocalTime.parse(hhmm)
    t.hour to t.minute
}.getOrDefault(5 to 0)
