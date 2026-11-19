package com.aragao.vab.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aragao.vab.VabApplication
import com.aragao.vab.core.ai.ModelRuntimeStatus
import com.aragao.vab.core.buddy.BuddyDna
import com.aragao.vab.core.markdown.MarkdownBoard
import com.aragao.vab.core.markdown.SectionRuleConfig
import com.aragao.vab.data.queue.QueueItemEntity
import com.aragao.vab.data.settings.VabSettings
import com.aragao.vab.overlay.FloatingBubbleService
import com.aragao.vab.overlay.WavAudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VabViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VabApplication
    private val inAppRecorder = WavAudioRecorder(application.applicationContext)

    val buddyDnaFlow: StateFlow<BuddyDna> = app.buddyDnaFlow

    val boardFlow: StateFlow<MarkdownBoard> = app.boardRepository.board
    val rawMarkdownFlow: StateFlow<String> = app.boardRepository.rawMarkdown
    val isRawEditorLockedFlow: StateFlow<Boolean> = app.boardRepository.isRawEditorLocked

    val queueItemsFlow: StateFlow<List<QueueItemEntity>> = app.queueOrchestrator.allItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeQueueCountFlow: StateFlow<Int> = app.queueOrchestrator.activeCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val celebrationTimestampFlow: StateFlow<Long> = app.queueOrchestrator.lastCelebrationTimestamp
    val latestInvalidNoticeFlow = app.queueOrchestrator.latestInvalidNotice

    private val _isTypingInApp = MutableStateFlow(false)
    val isTypingInApp: StateFlow<Boolean> = _isTypingInApp.asStateFlow()

    val settingsFlow: StateFlow<VabSettings> = app.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VabSettings())

    val modelStatusFlow: StateFlow<ModelRuntimeStatus> = app.modelManager.runtimeStatus

    val isRecordingInApp: StateFlow<Boolean> = inAppRecorder.isRecording
    val inAppAmplitude: StateFlow<Float> = inAppRecorder.amplitude

    private val _importingModel = MutableStateFlow(false)
    val importingModel: StateFlow<Boolean> = _importingModel.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.settingsRepository.getSnapshot()
            app.updateBuddySeed(s.customBuddySeed)
            app.modelManager.refreshDetectedModels(s.customWhisperModelPath, s.customSlmModelPath)
            if (s.floatingBubbleEnabled && Settings.canDrawOverlays(app)) {
                FloatingBubbleService.start(app)
            }
        }
    }

    fun randomizeBuddy() {
        viewModelScope.launch {
            val newSeed = kotlin.random.Random.nextLong()
            app.settingsRepository.setCustomBuddySeed(newSeed)
            app.updateBuddySeed(newSeed)
        }
    }

    fun resetDefaultBuddy() {
        viewModelScope.launch {
            app.settingsRepository.setCustomBuddySeed(null)
            app.updateBuddySeed(null)
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            val s = app.settingsRepository.getSnapshot()
            app.modelManager.refreshDetectedModels(s.customWhisperModelPath, s.customSlmModelPath)
        }
    }

    fun toggleTicket(ticketId: String) {
        viewModelScope.launch {
            app.boardRepository.toggleTicketCompleted(ticketId)
        }
    }

    fun deleteTicket(ticketId: String) {
        viewModelScope.launch {
            app.boardRepository.deleteTicket(ticketId)
        }
    }

    fun updateTicketSchedule(ticketId: String, deadline: String?, time: String?, reminderHours: Int) {
        viewModelScope.launch {
            app.boardRepository.updateTicketSchedule(ticketId, deadline, time, reminderHours)
        }
    }

    fun setRawEditorLocked(locked: Boolean) {
        app.boardRepository.setRawEditorLocked(locked)
    }

    fun saveRawMarkdownAndUnlock(newMarkdown: String) {
        viewModelScope.launch {
            app.boardRepository.saveRawMarkdown(newMarkdown)
            app.boardRepository.setRawEditorLocked(false)
        }
    }

    fun enqueueQuickText(text: String) {
        _isTypingInApp.value = false
        app.queueOrchestrator.enqueueText(text)
    }

    fun setTypingInApp(typing: Boolean) {
        _isTypingInApp.value = typing
    }

    fun dismissInvalidNotice() {
        app.queueOrchestrator.dismissInvalidNotice()
    }

    fun toggleInAppVoiceRecording(): Boolean {
        return if (inAppRecorder.isRecording.value) {
            viewModelScope.launch {
                val wav = inAppRecorder.stopRecording()
                if (wav != null) {
                    app.queueOrchestrator.enqueueAudio(wav)
                }
            }
            false
        } else {
            inAppRecorder.startRecording() != null
        }
    }

    fun undoQueueItem(itemId: Long) {
        app.queueOrchestrator.undoItem(itemId)
    }

    fun reprocessQueueItem(itemId: Long) {
        app.queueOrchestrator.reprocessItem(itemId)
    }

    fun clearHistory() {
        app.queueOrchestrator.clearFinishedHistory()
    }

    fun forceReleaseRam() {
        viewModelScope.launch {
            app.modelManager.forceReleaseAllRam()
        }
    }

    fun updateSections(sections: List<SectionRuleConfig>) {
        viewModelScope.launch {
            app.settingsRepository.updateSections(sections)
            app.boardRepository.syncConfiguredSectionsToBoard(sections)
        }
    }

    fun addSectionTab(name: String, description: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val current = app.settingsRepository.getSnapshot().sections
            if (current.any { it.name.equals(cleanName, ignoreCase = true) }) return@launch
            val updated = current + SectionRuleConfig(
                name = cleanName,
                description = description.trim().ifEmpty { "Assuntos e tarefas de $cleanName" }
            )
            app.settingsRepository.updateSections(updated)
            app.boardRepository.syncConfiguredSectionsToBoard(updated)
        }
    }

    fun removeSectionTab(name: String) {
        viewModelScope.launch {
            val current = app.settingsRepository.getSnapshot().sections
            if (current.size <= 1) return@launch
            val updated = current.filterNot { it.name.equals(name.trim(), ignoreCase = true) }
                .ifEmpty { VabSettings.defaultSections() }
            app.settingsRepository.updateSections(updated)
            app.boardRepository.syncConfiguredSectionsToBoard(updated)
        }
    }

    fun updatePersonalContext(contextText: String) {
        viewModelScope.launch {
            app.settingsRepository.updatePersonalContext(contextText)
        }
    }

    fun setFloatingBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !Settings.canDrawOverlays(app)) {
                app.settingsRepository.setFloatingBubbleEnabled(false)
                return@launch
            }
            app.settingsRepository.setFloatingBubbleEnabled(enabled)
            if (enabled) {
                FloatingBubbleService.start(app)
            } else {
                FloatingBubbleService.stop(app)
            }
        }
    }

    fun bindExternalSafFolder(treeUri: Uri) {
        viewModelScope.launch {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                app.contentResolver.takePersistableUriPermission(treeUri, flags)
            }
            app.settingsRepository.setExternalSafTreeUri(treeUri.toString())
            // Sincroniza o quadro atual imediatamente para a pasta escolhida
            app.boardRepository.saveBoard(app.boardRepository.board.value)
        }
    }

    fun unlinkExternalSafFolder() {
        viewModelScope.launch {
            app.settingsRepository.setExternalSafTreeUri(null)
        }
    }

    fun importCustomGgufModel(uri: Uri) {
        viewModelScope.launch {
            _importingModel.value = true
            withContext(Dispatchers.IO) {
                runCatching {
                    val destDir = File(app.filesDir, "models").apply { mkdirs() }
                    val destFile = File(destDir, "custom-slm.gguf")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { out ->
                            input.copyTo(out, bufferSize = 256 * 1024)
                        }
                    }
                    if (destFile.exists() && destFile.length() > 0L) {
                        app.settingsRepository.setCustomSlmModelPath(destFile.absolutePath)
                        app.modelManager.forceReleaseAllRam()
                        val s = app.settingsRepository.getSnapshot()
                        app.modelManager.refreshDetectedModels(s.customWhisperModelPath, s.customSlmModelPath)
                    }
                }
            }
            _importingModel.value = false
        }
    }

    fun resetToDefaultSlmModel() {
        viewModelScope.launch {
            app.settingsRepository.setCustomSlmModelPath(null)
            app.modelManager.forceReleaseAllRam()
            val s = app.settingsRepository.getSnapshot()
            app.modelManager.refreshDetectedModels(s.customWhisperModelPath, null)
        }
    }

    fun importCustomWhisperModel(uri: Uri) {
        viewModelScope.launch {
            _importingModel.value = true
            withContext(Dispatchers.IO) {
                runCatching {
                    val destDir = File(app.filesDir, "models").apply { mkdirs() }
                    val destFile = File(destDir, "custom-whisper.bin")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { out ->
                            input.copyTo(out, bufferSize = 256 * 1024)
                        }
                    }
                    if (destFile.exists() && destFile.length() > 0L) {
                        app.settingsRepository.setCustomWhisperModelPath(destFile.absolutePath)
                        app.modelManager.forceReleaseAllRam()
                        val s = app.settingsRepository.getSnapshot()
                        app.modelManager.refreshDetectedModels(s.customWhisperModelPath, s.customSlmModelPath)
                    }
                }
            }
            _importingModel.value = false
        }
    }

    fun resetToDefaultWhisperModel() {
        viewModelScope.launch {
            app.settingsRepository.setCustomWhisperModelPath(null)
            app.modelManager.forceReleaseAllRam()
            val s = app.settingsRepository.getSnapshot()
            app.modelManager.refreshDetectedModels(null, s.customSlmModelPath)
        }
    }
}
