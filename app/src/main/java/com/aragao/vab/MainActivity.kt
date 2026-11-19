package com.aragao.vab

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aragao.vab.core.buddy.AppIconManager
import com.aragao.vab.core.buddy.BuddyVisualState
import com.aragao.vab.ui.VabViewModel
import com.aragao.vab.ui.screens.BoardScreen
import com.aragao.vab.ui.screens.QueueScreen
import com.aragao.vab.ui.screens.SettingsScreen
import com.aragao.vab.ui.theme.VabPalette
import com.aragao.vab.ui.theme.VabTheme
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.parseColor("#FFF7F5F0"),
                Color.parseColor("#FFE2E0D8")
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.parseColor("#FFFFFFFF"),
                Color.parseColor("#FFE2E0D8")
            )
        )

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

                val buddyDna by vm.buddyDnaFlow.collectAsState()
                LaunchedEffect(buddyDna) {
                    AppIconManager.updateActivityTaskDescription(this@MainActivity, buddyDna)
                }
                val board by vm.boardFlow.collectAsState()
                val rawMd by vm.rawMarkdownFlow.collectAsState()
                val queueItems by vm.queueItemsFlow.collectAsState()
                val activeQueueCount by vm.activeQueueCountFlow.collectAsState()
                val settings by vm.settingsFlow.collectAsState()
                val modelStatus by vm.modelStatusFlow.collectAsState()
                val isRecordingVoice by vm.isRecordingInApp.collectAsState()
                val isTypingInApp by vm.isTypingInApp.collectAsState()
                val voiceAmplitude by vm.inAppAmplitude.collectAsState()
                val celebrationTs by vm.celebrationTimestampFlow.collectAsState()
                val importingModel by vm.importingModel.collectAsState()
                val latestInvalidNotice by vm.latestInvalidNoticeFlow.collectAsState()

                var isCelebrating by remember { mutableStateOf(false) }
                LaunchedEffect(celebrationTs) {
                    if (celebrationTs > 0L) {
                        isCelebrating = true
                        delay(2200L)
                        isCelebrating = false
                    }
                }

                val idleStates = remember {
                    listOf(
                        BuddyVisualState.IDLE_CHILL,
                        BuddyVisualState.IDLE_CHILL,
                        BuddyVisualState.IDLE_CURIOUS,
                        BuddyVisualState.IDLE_CHILL,
                        BuddyVisualState.IDLE_SLEEPY
                    )
                }
                var idleMoodIndex by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(20000L)
                        idleMoodIndex = (idleMoodIndex + 1) % idleStates.size
                    }
                }

                val buddyVisualState = when {
                    isRecordingVoice -> BuddyVisualState.LISTENING
                    isTypingInApp -> BuddyVisualState.NOTING
                    isCelebrating -> BuddyVisualState.CELEBRATING
                    activeQueueCount > 0 -> BuddyVisualState.ORGANIZING
                    board.hasUrgentDueOrOverdueP1(LocalDate.now().toString()) -> BuddyVisualState.IDLE_STRESSED_P1
                    else -> idleStates[idleMoodIndex]
                }

                val imeVisible = WindowInsets.isImeVisible

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (!imeVisible) {
                            androidx.compose.foundation.layout.Column {
                                HorizontalDivider(thickness = 1.dp, color = VabPalette.CardBorder)
                                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                    NavigationBarItem(
                                        selected = currentTab == 0,
                                        onClick = { currentTab = 0 },
                                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                        label = { Text("Fichário") }
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
                                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                                            }
                                        },
                                        label = { Text("Bastidores") }
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == 2,
                                        onClick = {
                                            vm.refreshModels()
                                            currentTab = 2
                                        },
                                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        label = { Text("Buddy") }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            0 -> BoardScreen(
                                buddyDna = buddyDna,
                                buddyVisualState = buddyVisualState,
                                audioAmplitude = voiceAmplitude,
                                isRecordingVoice = isRecordingVoice,
                                isTypingInApp = isTypingInApp,
                                activeQueueCount = activeQueueCount,
                                board = board,
                                rawMarkdown = rawMd,
                                queueItems = queueItems,
                                latestInvalidNotice = latestInvalidNotice,
                                onDismissInvalidNotice = vm::dismissInvalidNotice,
                                onOpenQueueTab = { currentTab = 1 },
                                onToggleTicket = vm::toggleTicket,
                                onDeleteTicket = vm::deleteTicket,
                                onUpdateTicketSchedule = vm::updateTicketSchedule,
                                onAddSectionTab = vm::addSectionTab,
                                onRemoveSectionTab = vm::removeSectionTab,
                                onSetRawEditorLocked = vm::setRawEditorLocked,
                                onSaveRawMarkdown = vm::saveRawMarkdownAndUnlock,
                                onTypingChanged = vm::setTypingInApp,
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
                                buddyDna = buddyDna,
                                settings = settings,
                                modelStatus = modelStatus,
                                importingModel = importingModel,
                                hasMicPermission = hasMicPerm,
                                onRequestMicPermission = {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                },
                                onRandomizeBuddy = vm::randomizeBuddy,
                                onResetDefaultBuddy = vm::resetDefaultBuddy,
                                onPinBuddyIcon = {
                                    AppIconManager.pinExactBuddyShortcut(context, buddyDna)
                                },
                                onToggleFloatingBubble = vm::setFloatingBubbleEnabled,
                                onUpdateSections = vm::updateSections,
                                onUpdatePersonalContext = vm::updatePersonalContext,
                                onBindExternalFolder = vm::bindExternalSafFolder,
                                onUnlinkExternalFolder = vm::unlinkExternalSafFolder,
                                onImportCustomGguf = vm::importCustomGgufModel,
                                onResetDefaultGguf = vm::resetToDefaultSlmModel,
                                onImportCustomWhisper = vm::importCustomWhisperModel,
                                onResetDefaultWhisper = vm::resetToDefaultWhisperModel
                            )
                        }
                    }
                }
            }
        }
    }
}
