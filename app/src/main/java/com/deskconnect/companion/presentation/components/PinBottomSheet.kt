package com.deskconnect.companion.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskconnect.companion.presentation.theme.*

enum class PinMode {
    AUTHENTICATE,
    SETUP_NEW,
    CHANGE_PIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinBottomSheet(
    mode: PinMode,
    onDismiss: () -> Unit,
    onPinSuccess: () -> Unit,
    verifyOldPin: (String) -> Boolean,
    saveNewPin: (String) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val title = when (mode) {
        PinMode.AUTHENTICATE -> "Enter Master PIN"
        PinMode.SETUP_NEW -> if (step == 0) "Create Master PIN" else "Confirm Master PIN"
        PinMode.CHANGE_PIN -> when (step) {
            0 -> "Enter Previous PIN"
            1 -> "Enter New Master PIN"
            else -> "Confirm New PIN"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)

            Spacer(modifier = Modifier.height(16.dp))

            // PIN Dots Display
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    val isFilled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) CyanAccent else SlateBorder)
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = errorMessage!!, color = CrimsonAbsent, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Numeric Keypad
            NumericKeypad(
                onNumberClick = { num ->
                    if (enteredPin.length < 4) {
                        enteredPin += num
                        errorMessage = null
                        if (enteredPin.length == 4) {
                            handlePinComplete(
                                mode = mode,
                                step = step,
                                currentInput = enteredPin,
                                confirmPin = confirmPin,
                                verifyOldPin = verifyOldPin,
                                saveNewPin = saveNewPin,
                                onSuccess = onPinSuccess,
                                onError = { msg ->
                                    errorMessage = msg
                                    enteredPin = ""
                                },
                                onAdvance = { nextStep, savedConfirm ->
                                    step = nextStep
                                    confirmPin = savedConfirm
                                    enteredPin = ""
                                }
                            )
                        }
                    }
                },
                onBackspace = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                        errorMessage = null
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun handlePinComplete(
    mode: PinMode,
    step: Int,
    currentInput: String,
    confirmPin: String,
    verifyOldPin: (String) -> Boolean,
    saveNewPin: (String) -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onAdvance: (Int, String) -> Unit
) {
    when (mode) {
        PinMode.AUTHENTICATE -> {
            if (verifyOldPin(currentInput)) onSuccess()
            else onError("Incorrect PIN. Try again.")
        }
        PinMode.SETUP_NEW -> {
            if (step == 0) {
                onAdvance(1, currentInput)
            } else {
                if (currentInput == confirmPin) {
                    saveNewPin(currentInput)
                    onSuccess()
                } else {
                    onError("PINs do not match. Start again.")
                    onAdvance(0, "")
                }
            }
        }
        PinMode.CHANGE_PIN -> {
            when (step) {
                0 -> {
                    if (verifyOldPin(currentInput)) onAdvance(1, "")
                    else onError("Previous PIN incorrect.")
                }
                1 -> onAdvance(2, currentInput)
                2 -> {
                    if (currentInput == confirmPin) {
                        saveNewPin(currentInput)
                        onSuccess()
                    } else {
                        onError("New PINs do not match.")
                        onAdvance(1, "")
                    }
                }
            }
        }
    }
}

@Composable
fun NumericKeypad(
    onNumberClick: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(if (key.isNotEmpty()) SlateSurfaceLight else Color.Transparent)
                            .clickable(enabled = key.isNotEmpty()) {
                                if (key == "DEL") onBackspace() else onNumberClick(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = TextWhite)
                        } else if (key.isNotEmpty()) {
                            Text(text = key, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                    }
                }
            }
        }
    }
}
