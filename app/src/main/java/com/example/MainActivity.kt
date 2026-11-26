package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.data.backup.LocalBackupExporter
import com.example.data.local.CallGuardDatabase
import com.example.data.repository.BlockedCallLogRepository
import com.example.data.repository.DeviceCallLogRepository
import com.example.data.repository.DeviceContactsRepository
import com.example.data.repository.PrefixRepository
import com.example.data.repository.SpamBlockerPreferencesRepository
import com.example.telecom.CallNotificationManager
import com.example.telecom.CallScreeningRoleHelper
import com.example.ui.CallGuardScreen
import com.example.ui.CallGuardViewModel
import com.example.ui.theme.CallGuardTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CallGuardViewModel by viewModels {
        val database = CallGuardDatabase.getInstance(applicationContext)
        val blockedCallLogRepository = BlockedCallLogRepository(database.blockedCallLogDao())
        val deviceContactsRepository = DeviceContactsRepository(applicationContext)
        CallGuardViewModel.Factory(
            prefixRepository = PrefixRepository(database.blockedPrefixDao()),
            callLogRepository = blockedCallLogRepository,
            deviceCallLogRepository = DeviceCallLogRepository(applicationContext),
            deviceContactsRepository = deviceContactsRepository,
            spamPrefsRepository = SpamBlockerPreferencesRepository(applicationContext),
            localBackupExporter = LocalBackupExporter(
                context = applicationContext,
                blockedCallLogRepository = blockedCallLogRepository,
                deviceContactsRepository = deviceContactsRepository
            ),
            initialStatus = CallScreeningRoleHelper.getStatus(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CallNotificationManager.ensureChannels(applicationContext)
        handleDialIntent(intent)

        setContent {
            CallGuardTheme {
                CallGuardScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDialIntent(intent)
    }

    private fun handleDialIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) {
            val telNumber = intent.data?.schemeSpecificPart
            viewModel.onExternalDialIntent(telNumber)
        }
    }
}
