package com.aragao.vab.data.queue

import com.aragao.vab.core.ai.GbnfGrammarBuilder
import com.aragao.vab.core.ai.ModelManager
import com.aragao.vab.core.ai.PromptBuilder
import com.aragao.vab.core.markdown.BoardPatchEngine
import com.aragao.vab.core.markdown.BoardRepository
import com.aragao.vab.core.markdown.DecisionPayload
import com.aragao.vab.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

class QueueOrchestrator(
    private val queueDao: QueueDao,
    private val boardRepository: BoardRepository,
    private val settingsRepository: SettingsRepository,
    private val modelManager: ModelManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workerMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _lastCelebrationTimestamp = MutableStateFlow(0L)
    val lastCelebrationTimestamp: StateFlow<Long> = _lastCelebrationTimestamp.asStateFlow()

    val allItemsFlow = queueDao.observeAll()
    val activeCountFlow = queueDao.observeActiveCount()

    init {
        // Quando o editor Markdown cru é desbloqueado (salvo/fechado), retoma itens WAITING_EDITOR
        scope.launch {
            boardRepository.isRawEditorLocked.collect { locked ->
                if (!locked) {
                    triggerProcessing()
                }
            }
        }
    }

    fun enqueueAudio(wavFile: File) {
        scope.launch {
            queueDao.insert(
                QueueItemEntity(
                    inputType = QueueInputType.AUDIO,
                    audioFilePath = wavFile.absolutePath,
                    status = QueueStatus.PENDING
                )
            )
            triggerProcessing()
        }
    }

    fun enqueueText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        scope.launch {
            queueDao.insert(
                QueueItemEntity(
                    inputType = QueueInputType.TEXT,
                    contentText = clean,
                    status = QueueStatus.PENDING
                )
            )
            triggerProcessing()
        }
    }

    fun undoItem(itemId: Long) {
        scope.launch {
            val item = queueDao.getById(itemId) ?: return@launch
            val snapshot = item.previousMarkdownSnapshot ?: return@launch
            if (item.status != QueueStatus.APPLIED) return@launch

            boardRepository.saveRawMarkdown(snapshot)
            queueDao.update(
                item.copy(
                    status = QueueStatus.UNDONE,
                    appliedSummary = "${item.appliedSummary} (Desfeito)"
                )
            )
        }
    }

    fun reprocessItem(itemId: Long) {
        scope.launch {
            val item = queueDao.getById(itemId) ?: return@launch
            queueDao.update(
                item.copy(
                    status = QueueStatus.PENDING,
                    errorMessage = null
                )
            )
            triggerProcessing()
        }
    }

    fun clearFinishedHistory() {
        scope.launch {
            queueDao.clearFinishedHistory()
        }
    }

    fun triggerProcessing() {
        scope.launch {
            if (workerMutex.isLocked) return@launch
            workerMutex.withLock {
                processLoop()
            }
        }
    }

    private suspend fun processLoop() {
        while (true) {
            val items = queueDao.getProcessableItems()
            if (items.isEmpty()) break

            val settings = settingsRepository.getSnapshot()

            // Fase 1: Transcreve em lote todos os áudios ainda sem texto antes de abrir o SLM
            val audioNeedingStt = items.filter {
                it.inputType == QueueInputType.AUDIO && it.contentText.isBlank()
            }
            if (audioNeedingStt.isNotEmpty()) {
                for (audioItem in audioNeedingStt) {
                    queueDao.update(audioItem.copy(status = QueueStatus.TRANSCRIBING, errorMessage = null))
                    val wavFile = audioItem.audioFilePath?.let(::File)
                    if (wavFile == null || !wavFile.exists()) {
                        queueDao.update(
                            audioItem.copy(
                                status = QueueStatus.FAILED,
                                errorMessage = "Arquivo de áudio não encontrado."
                            )
                        )
                        continue
                    }

                    val sttResult = modelManager.transcribeBatchAndReleaseWhisper(
                        wavFile = wavFile,
                        customWhisperPath = settings.customWhisperModelPath
                    )
                    sttResult.fold(
                        onSuccess = { text ->
                            if (text.isBlank()) {
                                queueDao.update(
                                    audioItem.copy(
                                        status = QueueStatus.FAILED,
                                        errorMessage = "Áudio silencioso ou nenhuma fala detectada."
                                    )
                                )
                            } else {
                                queueDao.update(
                                    audioItem.copy(
                                        contentText = text,
                                        status = QueueStatus.PENDING
                                    )
                                )
                            }
                        },
                        onFailure = { err ->
                            queueDao.update(
                                audioItem.copy(
                                    status = QueueStatus.FAILED,
                                    errorMessage = err.message ?: "Falha na transcrição Whisper"
                                )
                            )
                        }
                    )
                }
                modelManager.unloadWhisperNow()
            }

            // Fase 2: Processa decisões de tickets via Qwen + GBNF
            val readyForDecision = queueDao.getProcessableItems().filter { it.contentText.isNotBlank() }
            if (readyForDecision.isEmpty()) break

            // Se o usuário estiver editando o Markdown cru, aguarda salvar para não atropelar edições
            if (boardRepository.isRawEditorLocked.value) {
                for (waitingItem in readyForDecision) {
                    if (waitingItem.status != QueueStatus.WAITING_EDITOR) {
                        queueDao.update(waitingItem.copy(status = QueueStatus.WAITING_EDITOR))
                    }
                }
                break
            }

            var madeProgress = false
            for (item in readyForDecision) {
                if (boardRepository.isRawEditorLocked.value) {
                    queueDao.update(item.copy(status = QueueStatus.WAITING_EDITOR))
                    break
                }

                queueDao.update(item.copy(status = QueueStatus.DECIDING, errorMessage = null))

                val latestSettings = settingsRepository.getSnapshot()
                val currentBoard = boardRepository.reloadFromDisk()
                val previousMdSnapshot = boardRepository.rawMarkdown.first()

                val sectionNames = latestSettings.sections.map { it.name }
                val openIds = currentBoard.openTicketsWithSection().map { it.second.id }

                val gbnf = GbnfGrammarBuilder.build(
                    sectionNames = sectionNames,
                    openTicketIds = openIds
                )
                val sysPrompt = PromptBuilder.buildSystemPrompt(
                    sections = latestSettings.sections,
                    userPersonalContext = latestSettings.personalContext
                )
                val usrPrompt = PromptBuilder.buildUserPrompt(
                    userInputText = item.contentText,
                    board = currentBoard
                )

                val slmResult = modelManager.decideWithSlmAndScheduleUnload(
                    systemPrompt = sysPrompt,
                    userPrompt = usrPrompt,
                    gbnfGrammar = gbnf,
                    customSlmPath = latestSettings.customSlmModelPath
                )

                slmResult.fold(
                    onSuccess = { rawJson ->
                        val parsed = runCatching {
                            json.decodeFromString<DecisionPayload>(rawJson)
                        }
                        parsed.fold(
                            onSuccess = { payload ->
                                if (boardRepository.isRawEditorLocked.value) {
                                    queueDao.update(item.copy(status = QueueStatus.WAITING_EDITOR))
                                } else {
                                    val outcome = BoardPatchEngine.applyDecision(
                                        board = currentBoard,
                                        decision = payload,
                                        configuredSections = latestSettings.sections
                                    )
                                    boardRepository.saveBoard(outcome.updatedBoard)
                                    val summary = outcome.appliedDescriptions.joinToString(" • ")
                                        .ifBlank { "Nenhuma alteração necessária" }

                                    queueDao.update(
                                        item.copy(
                                            status = QueueStatus.APPLIED,
                                            rawDecisionJson = rawJson,
                                            appliedSummary = summary,
                                            previousMarkdownSnapshot = previousMdSnapshot,
                                            errorMessage = null
                                        )
                                    )
                                    _lastCelebrationTimestamp.value = System.currentTimeMillis()
                                    madeProgress = true
                                }
                            },
                            onFailure = { parseErr ->
                                queueDao.update(
                                    item.copy(
                                        status = QueueStatus.FAILED,
                                        rawDecisionJson = rawJson,
                                        errorMessage = "Erro ao interpretar JSON: ${parseErr.message}"
                                    )
                                )
                                madeProgress = true
                            }
                        )
                    },
                    onFailure = { err ->
                        queueDao.update(
                            item.copy(
                                status = QueueStatus.FAILED,
                                errorMessage = err.message ?: "Falha na inferência do SLM"
                            )
                        )
                        madeProgress = true
                    }
                )
            }

            if (!madeProgress) break
        }
    }
}
