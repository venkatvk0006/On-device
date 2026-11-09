package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calling.ContactCallDialog
import com.example.calling.ContactCallViewModel
import com.example.ui.ChatViewModel
import com.example.ui.MainChatScreen
import com.example.ui.theme.VenkatAiTheme
import com.example.util.configureAppCheck
import com.example.wakeword.WakeWordManager
import com.example.wakeword.WakeWordService

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val contactsGranted = permissions[Manifest.permission.READ_CONTACTS] == true

        if (recordAudioGranted) {
            val wakeWordManager = WakeWordManager.getInstance(this)
            if (wakeWordManager.settings.isWakeWordEnabled.value) {
                wakeWordManager.enableWakeWord()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        appContext = applicationContext

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        // STEP 2: Edge to edge support
        enableEdgeToEdge()

        // STEP 3: Request permissions for Microphone, Contacts, and Notifications
        requestAssistantPermissions()

        // STEP 4: Initialize UI
        setContent {
            val viewModel: ChatViewModel = viewModel()
            val contactCallViewModel: ContactCallViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()

            VenkatAiTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainChatScreen(
                        viewModel = viewModel,
                        contactCallViewModel = contactCallViewModel
                    )

                    ContactCallDialog(
                        viewModel = contactCallViewModel,
                        ttsHelper = viewModel.ttsHelper,
                        onDismiss = { contactCallViewModel.reset() }
                    )
                }
            }
        }

        // STEP 5: Process wake-up intent if launched from WakeWordService
        handleWakeWordIntent(intent)
    }

    private fun requestAssistantPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
        handleWakeWordIntent(intent)
    }

    private fun handleWakeWordIntent(intent: Intent?) {
        val wasTriggeredByWakeWord = intent?.getBooleanExtra(
            WakeWordService.EXTRA_WAKE_WORD_TRIGGERED,
            false
        ) == true

        if (wasTriggeredByWakeWord) {
            intent.removeExtra(WakeWordService.EXTRA_WAKE_WORD_TRIGGERED)
            Toast.makeText(this, "🐱 \"Hi Kitty\" detected! VENKAT AI is ready.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        var appContext: Context? = null
        var instance: MainActivity? = null
    }
}
