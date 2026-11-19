package com.aragao.vab

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aragao.vab.core.buddy.BuddyVisualState
import com.aragao.vab.ui.VabViewModel
import com.aragao.vab.ui.screens.BoardScreen
import com.aragao.vab.ui.screens.QueueScreen
import com.aragao.vab.ui.screens.SettingsScreen
import com.aragao.vab.ui.theme.VabTheme
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VabTheme {
                val vm: VabViewModel = viewModel()
                val context = LocalContext.current

                var currentTab by remember { mutableIntStateOf(0) }
                var hasMicPerm by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { perms ->
                    hasMicPerm = perms[Manifest.permission.RECORD_AUDIO] == true ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                }

                LaunchedEffect(Unit) {
                    val toRequest = mutableListOf<String>()
                    if (!hasMicPerm) {
                        toRequest.add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (toRequest.isNotEmpty()) {
                        permissionLauncher.launch(toRequest.toTypedArray())
                    }
                }

                val board by vm.boardFlow.collectAsState()
                val rawMd by vm.rawMarkdownFlow.collectAsState()
                val queueItems by vm.queueItemsFlow.collectAsState()
                val activeQueueCount by vm.activeQueueCountFlow.collectAsState()
                val settings by vm.settingsFlow.collectAsState()
                val modelStatus by vm.modelStatusFlow.collectAsState()
                val isRecordingVoice by vm.isRecordingInApp.collectAsState()
                val voiceAmplitude by vm.inAppAmplitude.collectAsState()
                val celebrationTs by vm.celebrationTimestampFlow.collectAsState()
                val importingModel by vm.importingModel.collectAsState()

                var isCelebrating by remember { mutableStateOf(false) }
                LaunchedEffect(celebrationTs) {
                    if (celebrationTs > 0L) {
                        isCelebrating = true
                        delay(2200L)
                        isCelebrating = false
                    }
                }

                val buddyVisualState = when {
                    isRecordingVoice -> BuddyVisualState.LISTENING
                    isCelebrating -> BuddyVisualState.CELEBRATING
                    activeQueueCount > 0 -> BuddyVisualState.THINKING
                    board.hasUrgentDueOrOverdueP1(LocalDate.now().toString()) -> BuddyVisualState.IDLE_STRESSED_P1
                    else -> BuddyVisualState.IDLE_CHILL
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                label = { Text("Seções") }
                            )
                            NavigationBarItem(
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (activeQueueCount > 0) {
                                                Badge { Text(activeQueueCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.QueueMusic, contentDescription = null)
                                    }
                                },
                                label = { Text("Fila & Undo") }
                            )
                            NavigationBarItem(
                                selected = currentTab == 2,
                                onClick = {
                                    vm.refreshModels()
                                    currentTab = 2
                                },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Buddy & Config") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentTab) {
                            0 -> BoardScreen(
                                buddyDna = vm.buddyDna,
                                buddyVisualState = buddyVisualState,
                                audioAmplitude = voiceAmplitude,
                                isRecordingVoice = isRecordingVoice,
                                activeQueueCount = activeQueueCount,
                                board = board,
                                rawMarkdown = rawMd,
                                onToggleTicket = vm::toggleTicket,
                                onDeleteTicket = vm::deleteTicket,
                                onSetRawEditorLocked = vm::setRawEditorLocked,
                                onSaveRawMarkdown = vm::saveRawMarkdownAndUnlock,
                                onEnqueueQuickText = vm::enqueueQuickText,
                                onToggleVoiceRecord = {
                                    if (!hasMicPerm) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    } else {
                                        vm.toggleInAppVoiceRecording()
                                    }
                                }
                            )
                            1 -> QueueScreen(
                                items = queueItems,
                                modelStatus = modelStatus,
                                onUndo = vm::undoQueueItem,
                                onReprocess = vm::reprocessQueueItem,
                                onClearHistory = vm::clearHistory,
                                onForceReleaseRam = vm::forceReleaseRam
                            )
                            2 -> SettingsScreen(
                                buddyDna = vm.buddyDna,
                                settings = settings,
                                modelStatus = modelStatus,
                                importingModel = importingModel,
                                hasMicPermission = hasMicPerm,
                                onRequestMicPermission = {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                },
                                onToggleFloatingBubble = vm::setFloatingBubbleEnabled,
                                onUpdateSections = vm::updateSections,
                                onUpdatePersonalContext = vm::updatePersonalContext,
                                onBindExternalFolder = vm::bindExternalSafFolder,
                                onUnlinkExternalFolder = vm::unlinkExternalSafFolder,
                                onImportCustomGguf = vm::importCustomGgufModel,
                                onResetDefaultGguf = vm::resetToDefaultSlmModel
                            )
                        }
                    }
                }
            }
        }
    }
}
