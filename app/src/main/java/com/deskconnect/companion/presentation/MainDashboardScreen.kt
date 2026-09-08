package com.deskconnect.companion.presentation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var showSnapshotViewer by remember { mutableStateOf(false) }

    // First time PIN setup prompt
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
            // Master Switch Card
            MasterSwitchCard(
                isOn = uiState.isMasterSwitchOn,
                onToggle = { turnOn ->
                    if (turnOn) {
                        pinMode = PinMode.AUTHENTICATE
                        onPinSuccessAction = { viewModel.toggleMasterSwitch(true) }
                        showPinSheet = true
                    } else {
                        pinMode = PinMode.AUTHENTICATE
                        onPinSuccessAction = { showPauseDialog = true }
                        showPinSheet = true
                    }
                }
            )

            // Paused State Card (If applicable)
            if (!uiState.isMasterSwitchOn && uiState.pauseUntilTimestamp > 0L) {
                PausedBanner(pauseTimestamp = uiState.pauseUntilTimestamp)
            }

            // Live Telemetry Hub (Battery & Heartbeat)
            LiveTelemetryCard(
                batteryLevel = uiState.batteryLevel,
                isCharging = uiState.isCharging,
                isHeartbeatAlive = uiState.isHeartbeatAlive,
                lastHeartbeatMs = uiState.lastHeartbeatMs
            )

            // Remote Snapshot Card
            SnapshotActionCard(
                latestSnapTime = uiState.latestSnapTime,
                hasSnapshot = uiState.latestSnapshotBase64 != null,
                onRequestSnap = { viewModel.requestSnapshot() },
                onViewSnap = { showSnapshotViewer = true }
            )

            // Event Log Navigation Button
            EventLogNavCard(onClick = onNavigateToEventLog)

            // Snooze Configuration Card
            SnoozeConfigCard(
                currentMinutes = uiState.snoozeMinutes,
                onSelectMinutes = { mins ->
                    pinMode = PinMode.AUTHENTICATE
                    onPinSuccessAction = { viewModel.updateSnoozeMinutes(mins) }
                    showPinSheet = true
                }
            )

            // Permissions Checklist (Auto-Hiding)
            PermissionSection(onPermissionsUpdated = { viewModel.loadLocalSettings() })
        }
    }

    // PIN BottomSheet Modal
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

    // Smart Pause Dialog
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

    // Base64 Snapshot Viewer Dialog
    if (showSnapshotViewer && uiState.latestSnapshotBase64 != null) {
        SnapshotDialog(
            base64String = uiState.latestSnapshotBase64!!,
            snapTimeMs = uiState.latestSnapTime,
            onDismiss = { showSnapshotViewer = false }
        )
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
fun LiveTelemetryCard(batteryLevel: Int, isCharging: Boolean, isHeartbeatAlive: Boolean, lastHeartbeatMs: Long) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "CAMERA TELEMETRY", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Battery Metric
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

                // Heartbeat Metric
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
                        Text(text = "Heartbeat Sync", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SnapshotActionCard(latestSnapTime: Long, hasSnapshot: Boolean, onRequestSnap: () -> Unit, onViewSnap: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "DESK SNAPSHOT", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(text = if (hasSnapshot) "Tap View to inspect photo" else "No image recorded yet", color = TextMuted, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasSnapshot) {
                    OutlinedButton(onClick = onViewSnap, shape = RoundedCornerShape(8.dp)) {
                        Text(text = "View", color = CyanAccent)
                    }
                }
                Button(onClick = onRequestSnap, colors = ButtonDefaults.buttonColors(containerColor = CyanAccent), shape = RoundedCornerShape(8.dp)) {
                    Text(text = "Request", color = SlateDark, fontWeight = FontWeight.Bold)
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
                Text(text = "View slot charts & presence breakdown", color = TextMuted, fontSize = 12.sp)
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
fun SnoozeConfigCard(currentMinutes: Int, onSelectMinutes: (Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "SNOOZE LIMIT (PIN PROTECTED)", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                (1..5).forEach { min ->
                    val isSelected = min == currentMinutes
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CyanAccent else SlateSurfaceLight)
                            .clickable { onSelectMinutes(min) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "${min}m", color = if (isSelected) SlateDark else TextWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
