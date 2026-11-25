package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.CallGuardDatabase
import com.example.data.repository.BlockedCallLogRepository
import com.example.data.repository.DeviceCallLogRepository
import com.example.data.repository.PrefixRepository
import com.example.data.repository.SpamBlockerPreferencesRepository
import com.example.telecom.CallScreeningRoleHelper
import com.example.ui.CallGuardScreen
import com.example.ui.CallGuardViewModel
import com.example.ui.theme.CallGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = CallGuardDatabase.getInstance(applicationContext)
        val prefixRepository = PrefixRepository(database.blockedPrefixDao())
        val callLogRepository = BlockedCallLogRepository(database.blockedCallLogDao())
        val deviceCallLogRepository = DeviceCallLogRepository(applicationContext)
        val spamPrefsRepository = SpamBlockerPreferencesRepository(applicationContext)
        val initialStatus = CallScreeningRoleHelper.getStatus(applicationContext)

        setContent {
            CallGuardTheme {
                val viewModel: CallGuardViewModel = viewModel(
                    factory = CallGuardViewModel.Factory(
                        prefixRepository = prefixRepository,
                        callLogRepository = callLogRepository,
                        deviceCallLogRepository = deviceCallLogRepository,
                        spamPrefsRepository = spamPrefsRepository,
                        initialStatus = initialStatus
                    )
                )
                CallGuardScreen(viewModel = viewModel)
            }
        }
    }
}
