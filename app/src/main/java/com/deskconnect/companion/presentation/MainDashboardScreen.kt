package com.deskconnect.companion.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskconnect.companion.data.local.QuietSlot
import com.deskconnect.companion.presentation.components.*
import com.deskconnect.companion.presentation.theme.*
import com.deskconnect.companion.presentation.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: MainViewModel,
    onNavigateToEventLog: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPinSheet by remember { mutableStateOf(false) }
    var pinMode by remember { mutableStateOf(PinMode.AUTHENTICATE) }
    var onPinSuccessAction by remember { mutableStateOf<() -> Unit>({}) }

    var showPauseDialog by remember { mutableStateOf(false) }
    var showSnapshotFullscreen by remember { mutableStateOf(false) }
    var slotToEdit by remember { mutableStateOf<QuietSlot?>(null) }

    // Smooth spin animation for Hard Refresh button
    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    LaunchedEffect(uiState.isPinSet) {
        if (!uiState.isPinSet) {
            pinMode = PinMode.SETUP_NEW
            onPinSuccessAction = { viewModel.loadLocalSettings() }
            showPinSheet = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DeskConnect", color = TextWhite, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("COMPANION", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    // HARD REFRESH BUTTON
                    IconButton(
                        onClick = { viewModel.hardRefresh() },
                        enabled = !uiState.isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Hard Refresh",
                            tint = CyanAccent,
                            modifier = Modifier.rotate(if (uiState.isRefreshing) spinAngle else 0f)
                        )
                    }

                    // PIN SETTINGS BUTTON
                    IconButton(onClick = {
                        pinMode = PinMode.CHANGE_PIN
                        onPinSuccessAction = { viewModel.loadLocalSettings() }
                        showPinSheet = true
                    }) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = "Change PIN", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDark)
            )
        },
        containerColor = SlateDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MasterSwitchCard(
                isOn = uiState.isMasterSwitchOn,
                onToggle = { turnOn ->
                    pinMode = PinMode.AUTHENTICATE
                    onPinSuccessAction = {
                        if (turnOn) viewModel.toggleMasterSwitch(true) else showPauseDialog = true
                    }
                    showPinSheet = true
                }
            )

            if (!uiState.isMasterSwitchOn && uiState.pauseUntilTimestamp > 0L) {
                PausedBanner(pauseTimestamp = uiState.pauseUntilTimestamp)
            }

            // Live Telemetry with Heartbeat Age (Latency)
            LiveTelemetryCard(
                batteryLevel = uiState.batteryLevel,
                isCharging = uiState.isCharging,
                isHeartbeatAlive = uiState.isHeartbeatAlive,
                heartbeatAgeSec = uiState.heartbeatAgeSec
            )

            InlineSnapshotCard(
                base64String = uiState.latestSnapshotBase64,
                latestSnapTime = uiState.latestSnapTime,
                onRequestSnap = { viewModel.requestSnapshot() },
                onImageClick = { showSnapshotFullscreen = true }
            )

            QuietSlotsCard(
                slots = uiState.quietSlots,
                onSlotClick = { clickedSlot ->
                    pinMode = PinMode.AUTHENTICATE
                    onPinSuccessAction = { slotToEdit = clickedSlot }
                    showPinSheet = true
                }
            )

            EventLogNavCard(onClick = onNavigateToEventLog)

            SnoozeConfigCard(
                currentSeconds = uiState.snoozeSeconds,
                onSelectSeconds = { secs ->
                    pinMode = PinMode.AUTHENTICATE
                    onPinSuccessAction = { viewModel.updateSnoozeSeconds(secs) }
                    showPinSheet = true
                }
            )

            PermissionSection(onPermissionsUpdated = { viewModel.loadLocalSettings() })
        }
    }

    if (showPinSheet) {
        PinBottomSheet(
            mode = pinMode,
            onDismiss = { showPinSheet = false },
            onPinSuccess = {
                showPinSheet = false
                onPinSuccessAction()
            },
            verifyOldPin = { input -> viewModel.prefs.verifyPin(input) },
            saveNewPin = { newPin -> viewModel.prefs.setMasterPin(newPin) }
        )
    }

    if (showPauseDialog) {
        PauseDurationDialog(
            onDismiss = { showPauseDialog = false },
            onSelectUnlimited = {
                showPauseDialog = false
                viewModel.toggleMasterSwitch(false)
            },
            onSelectSpecificDate = { millis ->
                showPauseDialog = false
                viewModel.pauseUntilDate(millis)
            }
        )
    }

    if (slotToEdit != null) {
        QuietSlotEditDialog(
            slot = slotToEdit!!,
            onDismiss = { slotToEdit = null },
            onSave = { updatedSlot ->
                val currentSlots = uiState.quietSlots.toMutableList()
                val idx = currentSlots.indexOfFirst { it.id == updatedSlot.id }
                if (idx != -1) {
                    currentSlots[idx] = updatedSlot
                    viewModel.updateQuietSlots(currentSlots)
                }
                slotToEdit = null
            }
        )
    }

    if (showSnapshotFullscreen && uiState.latestSnapshotBase64 != null) {
        SnapshotDialog(
            base64String = uiState.latestSnapshotBase64!!,
            snapTimeMs = uiState.latestSnapTime,
            onDismiss = { showSnapshotFullscreen = false }
        )
    }
}

@Composable
fun LiveTelemetryCard(
    batteryLevel: Int,
    isCharging: Boolean,
    isHeartbeatAlive: Boolean,
    heartbeatAgeSec: Long
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "CAMERA TELEMETRY", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Battery section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (batteryLevel > 20) EmeraldPresent else CrimsonAbsent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "$batteryLevel%", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(text = if (isCharging) "Charging (Plugged)" else "On Battery", color = TextMuted, fontSize = 11.sp)
                    }
                }

                // Heartbeat & Live Latency section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (isHeartbeatAlive) EmeraldPresent else CrimsonAbsent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isHeartbeatAlive) "ONLINE" else "DISCONNECTED",
                            color = if (isHeartbeatAlive) EmeraldPresent else CrimsonAbsent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val latencyLabel = when {
                            heartbeatAgeSec < 0 -> "Syncing..."
                            heartbeatAgeSec == 0L -> "Just now"
                            else -> "${heartbeatAgeSec}s ago"
                        }
                        Text(
                            text = "Heartbeat: $latencyLabel",
                            color = if (isHeartbeatAlive) CyanAccent else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InlineSnapshotCard(
    base64String: String?,
    latestSnapTime: Long,
    onRequestSnap: () -> Unit,
    onImageClick: () -> Unit
) {
    val bitmap: Bitmap? = remember(base64String) {
        if (base64String.isNullOrEmpty()) null
        else {
            try {
                val cleanBase64 = base64String.substringAfter(",")
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (_: Exception) {
                null
            }
        }
    }

    val timeFormatted = remember(latestSnapTime) {
        if (latestSnapTime > 0) SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(latestSnapTime))
        else "No capture yet"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "DESK LIVE SNAPSHOT", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = "Last: $timeFormatted", color = TextMuted, fontSize = 11.sp)
                }

                Button(
                    onClick = onRequestSnap,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = SlateDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Request Snap", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateDark)
                        .clickable { onImageClick() },
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Desk Snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "Tap to enlarge", color = Color.White, fontSize = 10.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No snapshot recorded yet. Tap 'Request Snap'", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MasterSwitchCard(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isOn) CyanAccent else SlateBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "MASTER SWITCH", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = if (isOn) "Monitoring Active (24/7)" else "System Suspended",
                    color = if (isOn) EmeraldPresent else TextMuted,
                    fontSize = 13.sp
                )
            }
            Switch(
                checked = isOn,
                onCheckedChange = { onToggle(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = SlateDark, checkedTrackColor = CyanAccent)
            )
        }
    }
}

@Composable
fun PausedBanner(pauseTimestamp: Long) {
    val formatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(pauseTimestamp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x22F59E0B), RoundedCornerShape(12.dp))
            .border(1.dp, AmberBreak, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.HourglassBottom, contentDescription = null, tint = AmberBreak)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "MONITORING PAUSED", color = AmberBreak, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(text = "Auto-resumes on: $formatted", color = TextWhite, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun QuietSlotsCard(slots: List<QuietSlot>, onSlotClick: (QuietSlot) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("4 SILENT SLOTS (AUTO-MUTE)", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Tap to edit (PIN)", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            slots.forEach { slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateSurfaceLight)
                        .clickable { onSlotClick(slot) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (slot.isEnabled) EmeraldPresent else TextMuted)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Slot ${slot.id}:", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${formatTime12H(slot.startHour, slot.startMinute)} - ${formatTime12H(slot.endHour, slot.endMinute)}",
                            color = if (slot.isEnabled) TextWhite else TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = if (slot.isEnabled) "ACTIVE" else "OFF",
                        color = if (slot.isEnabled) EmeraldPresent else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EventLogNavCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateSurface, RoundedCornerShape(16.dp))
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Assessment, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = "Study Event Logs", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(text = "All-day reports & presence breakdown", color = TextMuted, fontSize = 12.sp)
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
fun SnoozeConfigCard(currentSeconds: Int, onSelectSeconds: (Int) -> Unit) {
    val options = listOf(
        30 to "30s",
        60 to "1m",
        120 to "2m",
        180 to "3m",
        300 to "5m"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "SNOOZE LIMIT (PIN PROTECTED)", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                options.forEach { (sec, label) ->
                    val isSelected = sec == currentSeconds
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CyanAccent else SlateSurfaceLight)
                            .clickable { onSelectSeconds(sec) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) SlateDark else TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
