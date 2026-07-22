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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    /** True once a masjid has been persisted — gates the explicit Delete action. */
    val hasExisting: Boolean = false,
    /** Set after an explicit, confirmed delete so the screen can navigate back. */
    val deleted: Boolean = false,
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
                    hasExisting = existing != null,
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
     * Explicit, destructive removal of the saved masjid. Only reached from the
     * confirmed "Delete saved masjid" action — switching source to GPS never calls
     * this. [PrayerSourceRepository.clearLocalMasjid] deletes the row and resets the
     * active source to API so GPS keeps working afterwards.
     */
    fun deleteMasjid() {
        viewModelScope.launch {
            repo.clearLocalMasjid()
            _state.update { it.copy(deleted = true) }
        }
    }

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

    // Auto-navigate back on save success or after an explicit delete.
    LaunchedEffect(state.saveSuccess, state.deleted) {
        if (state.saveSuccess || state.deleted) onBack()
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

            // BM-HOME-PRAYER-UX — official Material 3 Time Picker. Value is committed to
            // state only on OK; Cancel preserves the previous value. Stored as "HH:mm".
            editingKey?.let { key ->
                val entry = (fields + jummaFields).first { it.third == key }
                M3TimePickerDialog(
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

            // Explicit destructive action (separate from switching source to GPS).
            // Only shown once a masjid is actually persisted.
            if (state.hasExisting) {
                var showDeleteConfirm by remember { mutableStateOf(false) }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Delete saved masjid", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete saved masjid?") },
                        text = {
                            Text(
                                "This permanently removes “${state.masjidName.ifBlank { "your masjid" }}” and its saved prayer times. " +
                                    "Niyyah will switch to GPS / calculated times. This cannot be undone."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showDeleteConfirm = false; viewModel.deleteMasjid() }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                        },
                    )
                }
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
    val context = LocalContext.current
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
                    text = if (v.isBlank()) "Set time" else displayTime(context, v),
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
 * BM-HOME-PRAYER-UX — official Material 3 Time Picker in a themed dialog.
 *
 * Uses [TimePicker]/[rememberTimePickerState] with a dial ⇄ keyboard ([TimeInput])
 * toggle, exactly as documented for M3. Behaviour is deliberately unmodified:
 *  - [is24Hour] follows the device convention ([DateFormat.is24HourFormat]).
 *  - the value is emitted to the caller ONLY on OK; Cancel/dismiss preserves the old one.
 *  - vertical scroll + capped width keep the dial usable under large font/display scaling
 *    and portrait insets. Surface uses the app theme so light/dark/AMOLED are correct.
 * Storage stays canonical "HH:mm" (24h) — see [LocalMasjidViewModel.update].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun M3TimePickerDialog(
    label: String,
    initial: String,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val (initHour, initMinute) = parseHHmm(initial)
    val state = rememberTimePickerState(
        initialHour = initHour,
        initialMinute = initMinute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    var keyboardEntry by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Set $label time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))

                if (keyboardEntry) TimeInput(state = state) else TimePicker(state = state)

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { keyboardEntry = !keyboardEntry }) {
                        Icon(
                            imageVector = if (keyboardEntry) Icons.Outlined.Schedule else Icons.Outlined.Keyboard,
                            contentDescription = if (keyboardEntry) "Switch to clock" else "Switch to keyboard",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                        Text("OK", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * "HH:mm" (24h) → localized display, respecting the device 12/24-hour convention.
 * Formatting is presentation-only; storage stays canonical "HH:mm".
 */
private fun displayTime(context: android.content.Context, hhmm: String): String = runCatching {
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    java.time.LocalTime.parse(hhmm).format(java.time.format.DateTimeFormatter.ofPattern(pattern))
}.getOrDefault(hhmm)

/** "HH:mm" → (hour, minute); defaults to 05:00 when blank/invalid. */
private fun parseHHmm(hhmm: String): Pair<Int, Int> = runCatching {
    val t = java.time.LocalTime.parse(hhmm)
    t.hour to t.minute
}.getOrDefault(5 to 0)
