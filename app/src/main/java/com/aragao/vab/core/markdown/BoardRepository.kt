package com.aragao.vab.core.markdown

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.aragao.vab.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class BoardRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val BOARD_FILENAME = "vab_board.md"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileMutex = Mutex()
    private val internalBoardFile = File(context.filesDir, BOARD_FILENAME)

    private val _rawMarkdown = MutableStateFlow("")
    val rawMarkdown: StateFlow<String> = _rawMarkdown.asStateFlow()

    private val _board = MutableStateFlow(MarkdownBoard())
    val board: StateFlow<MarkdownBoard> = _board.asStateFlow()

    /**
     * Indica que o usuário está com o editor Markdown cru aberto com edições em andamento.
     * Enquanto true, a fila não sobrescreve o arquivo até o usuário salvar ou sair.
     */
    private val _isRawEditorLocked = MutableStateFlow(false)
    val isRawEditorLocked: StateFlow<Boolean> = _isRawEditorLocked.asStateFlow()

    init {
        scope.launch {
            reloadFromDisk()
        }
    }

    fun setRawEditorLocked(locked: Boolean) {
        _isRawEditorLocked.value = locked
    }

    suspend fun reloadFromDisk(): MarkdownBoard = fileMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.getSnapshot()
            val sectionNames = settings.sections.map { it.name }

            // Se houver pasta externa SAF (ex: Obsidian), tenta ler de lá caso mais recente
            val externalContent = settings.externalSafTreeUri?.let { readExternalSafFile(it) }
            val text = when {
                !externalContent.isNullOrBlank() -> {
                    internalBoardFile.writeText(externalContent)
                    externalContent
                }
                internalBoardFile.exists() -> internalBoardFile.readText()
                else -> {
                    val initialBoard = MarkdownBoard(
                        sections = sectionNames.map { BoardSection(name = it) }
                    )
                    val initialMd = MarkdownParser.serialize(initialBoard)
                    internalBoardFile.writeText(initialMd)
                    initialMd
                }
            }

            val parsed = MarkdownParser.parse(text, sectionNames)
            val normalizedBoard = alignBoardWithConfiguredSections(parsed, sectionNames)
            val finalMd = if (normalizedBoard != parsed) {
                val updated = MarkdownParser.serialize(normalizedBoard)
                internalBoardFile.writeText(updated)
                settings.externalSafTreeUri?.let { writeExternalSafFile(it, updated) }
                updated
            } else {
                text
            }
            _rawMarkdown.value = finalMd
            _board.value = normalizedBoard
            normalizedBoard
        }
    }

    suspend fun saveRawMarkdown(newMarkdown: String) = fileMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.getSnapshot()
            val parsed = MarkdownParser.parse(newMarkdown, settings.sections.map { it.name })
            val normalizedText = if (newMarkdown.endsWith("\n")) newMarkdown else "$newMarkdown\n"

            internalBoardFile.writeText(normalizedText)
            settings.externalSafTreeUri?.let { writeExternalSafFile(it, normalizedText) }

            _rawMarkdown.value = normalizedText
            _board.value = parsed
        }
    }

    suspend fun saveBoard(updatedBoard: MarkdownBoard, sortTickets: Boolean = true): String = fileMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.getSnapshot()
            val serialized = MarkdownParser.serialize(updatedBoard, sortTickets = sortTickets)
            internalBoardFile.writeText(serialized)
            settings.externalSafTreeUri?.let { writeExternalSafFile(it, serialized) }

            _rawMarkdown.value = serialized
            _board.value = MarkdownParser.parse(serialized, settings.sections.map { it.name })
            serialized
        }
    }

    suspend fun toggleTicketCompleted(ticketId: String) {
        val current = _board.value
        val updatedSections = current.sections.map { sec ->
            val updatedTickets = sec.tickets.map { t ->
                if (t.id == ticketId) t.copy(completed = !t.completed) else t
            }
            sec.copy(tickets = MarkdownParser.sortSectionTickets(updatedTickets))
        }
        saveBoard(current.copy(sections = updatedSections))
    }

    suspend fun deleteTicket(ticketId: String) {
        val current = _board.value
        val updatedSections = current.sections.map { sec ->
            sec.copy(tickets = sec.tickets.filterNot { it.id == ticketId })
        }
        saveBoard(current.copy(sections = updatedSections))
    }

    suspend fun updateTicketSchedule(ticketId: String, deadline: String?, time: String? = null, reminderHours: Int) {
        val cleanDeadline = deadline?.trim()?.takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
        val cleanTime = time?.trim()?.takeIf { it.matches(Regex("""([01]\d|2[0-3]):[0-5]\d""")) }
        val cleanReminder = if (cleanDeadline == null && cleanTime == null) {
            0
        } else if (reminderHours in listOf(0, 15, 30, 60, 120, 1440)) {
            reminderHours
        } else {
            30
        }
        val current = _board.value
        val updatedSections = current.sections.map { sec ->
            val updatedTickets = sec.tickets.map { t ->
                if (t.id == ticketId) {
                    t.copy(
                        deadline = cleanDeadline,
                        time = cleanTime,
                        reminderHours = cleanReminder
                    )
                } else {
                    t
                }
            }
            sec.copy(tickets = MarkdownParser.sortSectionTickets(updatedTickets))
        }
        saveBoard(current.copy(sections = updatedSections))
    }

    suspend fun syncConfiguredSectionsToBoard(sections: List<SectionRuleConfig>) {
        val current = _board.value
        val merged = alignBoardWithConfiguredSections(current, sections.map { it.name })
        saveBoard(merged)
    }

    private fun alignBoardWithConfiguredSections(
        current: MarkdownBoard,
        configuredNames: List<String>
    ): MarkdownBoard {
        val safeNames = configuredNames.ifEmpty { listOf("Geral") }
        val existingByLower = current.sections.associateBy { it.name.lowercase() }
        val merged = mutableListOf<BoardSection>()

        for (name in safeNames) {
            val found = existingByLower[name.lowercase()]
            if (found != null) {
                merged.add(found.copy(name = name))
            } else {
                merged.add(BoardSection(name = name))
            }
        }
        // Mantém seções extras somente se tiverem tickets ou linhas livres (remove abas antigas vazias)
        for (sec in current.sections) {
            val alreadyIncluded = merged.any { it.name.equals(sec.name, ignoreCase = true) }
            if (!alreadyIncluded && (sec.tickets.isNotEmpty() || sec.freeformLines.isNotEmpty())) {
                merged.add(sec)
            }
        }
        return current.copy(sections = merged)
    }

    private fun readExternalSafFile(treeUriString: String): String? {
        return runCatching {
            val treeUri = Uri.parse(treeUriString)
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            val doc = dir.findFile(BOARD_FILENAME) ?: return null
            context.contentResolver.openInputStream(doc.uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
    }

    private fun writeExternalSafFile(treeUriString: String, content: String) {
        runCatching {
            val treeUri = Uri.parse(treeUriString)
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return
            val doc = dir.findFile(BOARD_FILENAME)
                ?: dir.createFile("text/markdown", BOARD_FILENAME)
                ?: return
            context.contentResolver.openOutputStream(doc.uri, "wt")?.bufferedWriter()?.use {
                it.write(content)
            }
        }
    }
}
