package com.deskconnect.companion.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.deskconnect.companion.presentation.eventlog.EventLogScreen
import com.deskconnect.companion.presentation.theme.DeskConnectTheme
import com.deskconnect.companion.presentation.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = MainViewModel(applicationContext)

        setContent {
            DeskConnectTheme {
                val uiState by viewModel.uiState.collectAsState()
                var currentScreen by remember { mutableStateOf("DASHBOARD") }
                var selectedDate by remember { mutableStateOf("") }

                if (currentScreen == "DASHBOARD") {
                    MainDashboardScreen(
                        viewModel = viewModel,
                        onNavigateToEventLog = {
                            if (uiState.availableDates.isNotEmpty()) {
                                selectedDate = uiState.availableDates.first()
                                viewModel.loadEventDatePayload(selectedDate)
                            }
                            currentScreen = "EVENT_LOG"
                        }
                    )
                } else {
                    EventLogScreen(
                        availableDates = uiState.availableDates,
                        selectedDate = selectedDate,
                        onDateSelected = { date ->
                            selectedDate = date
                            viewModel.loadEventDatePayload(date)
                        },
                        dailyPayload = uiState.selectedDatePayload,
                        onBackClicked = { currentScreen = "DASHBOARD" }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLocalSettings()
    }
}
