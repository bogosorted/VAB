package com.aragao.vab.core.markdown

import java.text.Normalizer
import java.time.LocalDate
import java.util.UUID

data class PatchOutcome(
    val updatedBoard: MarkdownBoard,
    val appliedDescriptions: List<String>
)

object BoardPatchEngine {

    fun applyDecision(
        board: MarkdownBoard,
        decision: DecisionPayload,
        configuredSections: List<SectionRuleConfig>,
        rawUserInput: String = "",
        today: LocalDate = LocalDate.now()
    ): PatchOutcome {
        if (!decision.isValidDecision || decision.operations.isEmpty()) {
            return PatchOutcome(board, emptyList())
        }

        val hasRawInput = rawUserInput.isNotBlank()
        val spokenTime = if (hasRawInput) extractExplicitTimeFromText(rawUserInput) else null
        val spokenDeadline = if (hasRawInput) extractExplicitDeadlineFromText(rawUserInput, today) else null
        val userMentionedAnyDate = if (hasRawInput) hasExplicitDateReference(rawUserInput) else true
        val spokenSectionMatch = if (hasRawInput) findMentionedSection(rawUserInput, configuredSections) else null

        val validSectionNames = configuredSections.map { it.name }.ifEmpty {
            board.sections.map { it.name }.ifEmpty { listOf("Pendências") }
        }
        val defaultSection = validSectionNames.first()

        // Mapa mutável preservando a ordem das seções existentes + configuradas
        val sectionOrder = linkedSetOf<String>()
        board.sections.forEach { sectionOrder.add(it.name) }
        validSectionNames.forEach { sectionOrder.add(it) }

        val sectionTickets = mutableMapOf<String, MutableList<Ticket>>()
        val sectionFreeform = mutableMapOf<String, List<String>>()

        for (secName in sectionOrder) {
            val existing = board.sections.find { it.name.equals(secName, ignoreCase = true) }
            sectionTickets[secName] = existing?.tickets?.toMutableList() ?: mutableListOf()
            sectionFreeform[secName] = existing?.freeformLines ?: emptyList()
        }

        fun resolveSectionName(requested: String?): String {
            if (requested.isNullOrBlank()) return defaultSection
            return sectionOrder.firstOrNull { it.equals(requested.trim(), ignoreCase = true) }
                ?: defaultSection
        }

        fun findTicketById(targetId: String): Pair<String, Int>? {
            for ((secName, list) in sectionTickets) {
                val idx = list.indexOfFirst { it.id.equals(targetId.trim(), ignoreCase = true) }
                if (idx >= 0) return secName to idx
            }
            return null
        }

        val existingIds = sectionTickets.values.flatten().map { it.id }.toMutableSet()
        fun nextUniqueTicketId(): String {
            while (true) {
                val candidate = "t_" + UUID.randomUUID().toString().replace("-", "").take(4)
                if (existingIds.add(candidate)) return candidate
            }
        }

        val logs = mutableListOf<String>()

        for (op in decision.operations) {
            when (op.op.trim().lowercase()) {
                "create" -> {
                    val rawTitle = op.title?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val cleanTitle = if (hasRawInput) {
                        anchorTitleToWhisperInput(rawTitle, rawUserInput)
                    } else {
                        rawTitle
                    }
                    val targetSec = spokenSectionMatch ?: resolveSectionName(op.section)
                    val prio = Priority.fromString(op.priority)
                    // NUNCA permite que o Qwen 0.5B invente horário ("08:00") ou data que o usuário não falou!
                    val deadline = when {
                        !hasRawInput -> sanitizeDeadline(op.deadline)
                        spokenDeadline != null -> spokenDeadline
                        userMentionedAnyDate -> sanitizeDeadline(op.deadline)
                        else -> null
                    }
                    val time = when {
                        !hasRawInput -> sanitizeTime(op.time)
                        else -> spokenTime
                    }
                    val reminderMinutes = sanitizeNotifyBeforeMinutes(op.reminder_hours, deadline, time)
                    val newId = nextUniqueTicketId()
                    val newTicket = Ticket(
                        id = newId,
                        title = cleanTitle,
                        priority = prio,
                        completed = false,
                        deadline = deadline,
                        time = time,
                        reminderHours = reminderMinutes,
                        notes = op.notes?.trim().orEmpty()
                    )
                    sectionTickets.getOrPut(targetSec) { mutableListOf() }.add(newTicket)
                    logs.add("Criado [${prio.name}] \"$cleanTitle\" em $targetSec ($newId)")
                }

                "complete" -> {
                    val tid = op.target_id?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val loc = findTicketById(tid) ?: continue
                    val current = sectionTickets[loc.first]!![loc.second]
                    sectionTickets[loc.first]!![loc.second] = current.copy(completed = true, reminderHours = 0)
                    logs.add("Concluído \"${current.title}\" ($tid)")
                }

                "update" -> {
                    val tid = op.target_id?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val loc = findTicketById(tid) ?: continue
                    val current = sectionTickets[loc.first]!![loc.second]
                    val nextPrio = if (op.priority != null) Priority.fromString(op.priority) else current.priority
                    val nextDeadline = when {
                        !hasRawInput -> if (op.deadline != null) (sanitizeDeadline(op.deadline) ?: current.deadline) else current.deadline
                        spokenDeadline != null -> spokenDeadline
                        userMentionedAnyDate && op.deadline != null -> sanitizeDeadline(op.deadline) ?: current.deadline
                        else -> current.deadline
                    }
                    val nextTime = when {
                        !hasRawInput -> if (op.time != null) sanitizeTime(op.time) else current.time
                        spokenTime != null -> spokenTime
                        else -> current.time
                    }
                    val nextReminder = if (op.reminder_hours != null) {
                        sanitizeNotifyBeforeMinutes(op.reminder_hours, nextDeadline, nextTime)
                    } else if (nextDeadline == null && nextTime == null) {
                        0
                    } else {
                        current.reminderHours.takeIf { it > 0 } ?: 30
                    }

                    val updatedTicket = current.copy(
                        title = op.title?.trim()?.takeIf { it.isNotEmpty() } ?: current.title,
                        priority = nextPrio,
                        deadline = nextDeadline,
                        time = nextTime,
                        reminderHours = nextReminder,
                        notes = op.notes?.trim()?.takeIf { it.isNotEmpty() } ?: current.notes
                    )

                    val newSec = spokenSectionMatch ?: if (!op.section.isNullOrBlank()) resolveSectionName(op.section) else loc.first
                    if (newSec != loc.first) {
                        sectionTickets[loc.first]!!.removeAt(loc.second)
                        sectionTickets.getOrPut(newSec) { mutableListOf() }.add(updatedTicket)
                        logs.add("Movido/atualizado \"${updatedTicket.title}\" para $newSec [${updatedTicket.priority.name}] ($tid)")
                    } else {
                        sectionTickets[loc.first]!![loc.second] = updatedTicket
                        logs.add("Atualizado \"${updatedTicket.title}\" [${updatedTicket.priority.name}] ($tid)")
                    }
                }
            }
        }

        val rebuiltSections = sectionOrder.map { secName ->
            BoardSection(
                name = secName,
                tickets = MarkdownParser.sortSectionTickets(sectionTickets[secName].orEmpty()),
                freeformLines = sectionFreeform[secName].orEmpty()
            )
        }

        return PatchOutcome(
            updatedBoard = board.copy(sections = rebuiltSections),
            appliedDescriptions = logs
        )
    }

    private fun sanitizeDeadline(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true) || trimmed.equals("none", ignoreCase = true)) {
            return null
        }
        return if (trimmed.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) trimmed else null
    }

    private fun sanitizeTime(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true) || trimmed.equals("none", ignoreCase = true)) {
            return null
        }
        return if (trimmed.matches(Regex("""([01]\d|2[0-3]):[0-5]\d"""))) trimmed else null
    }

    /**
     * Converte `reminder_hours` / minutos de antecedência:
     * - Se não houver nem data nem horário, SEMPRE retorna 0 (sem notificação).
     * - Se houver data/horário, padrão é 30 minutos antes (30).
     */
    private fun sanitizeNotifyBeforeMinutes(rawVal: Int?, deadline: String?, time: String?): Int {
        if (deadline == null && time == null) return 0
        return when (rawVal) {
            15, 30, 60, 120, 1440 -> rawVal
            2 -> 30 // compatibilidade: converte antigo '2' para 30min antes
            6 -> 60
            24 -> 1440
            0 -> 0
            null -> 30
            else -> 30
        }
    }

    private fun stripAccents(input: String): String {
        return Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
    }

    internal fun hasExplicitDateReference(userInput: String): Boolean {
        val norm = stripAccents(userInput)
        return Regex(
            """\b(hoje|amanha|depois|segunda|terca|quarta|quinta|sexta|sabado|domingo|semana|mes|dia|20\d{2}-\d{2}-\d{2}|\d{1,2}/\d{1,2})\b"""
        ).containsMatchIn(norm)
    }

    internal fun extractExplicitDeadlineFromText(userInput: String, today: LocalDate): String? {
        val norm = stripAccents(userInput)
        return when {
            Regex("""\bdepois\s+de\s+amanha\b""").containsMatchIn(norm) -> today.plusDays(2).toString()
            Regex("""\bamanha\b""").containsMatchIn(norm) -> today.plusDays(1).toString()
            Regex("""\bhoje\b""").containsMatchIn(norm) -> today.toString()
            else -> Regex("""\b(20\d{2}-\d{2}-\d{2})\b""").find(norm)?.groupValues?.get(1)
        }
    }

    internal fun extractExplicitTimeFromText(userInput: String): String? {
        val norm = stripAccents(userInput)
        val hhmm = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""").find(norm)
        if (hhmm != null) {
            val h = hhmm.groupValues[1].toInt()
            val m = hhmm.groupValues[2].toInt()
            return String.format("%02d:%02d", h, m)
        }
        val spoken = Regex(
            """\b(?:as\s+(\d{1,2})(?:\s*e\s*(\d{1,2}))?(?:\s*h(?:oras?)?)?(?:\s+da\s+(manha|tarde|noite))?|(\d{1,2})\s*(?:h(?:oras?)(?:\s*e\s*(\d{1,2}))?|\s+da\s+(manha|tarde|noite)))\b"""
        ).find(norm) ?: return null

        val hRaw = spoken.groupValues[1].ifEmpty { spoken.groupValues[4] }.toIntOrNull() ?: return null
        val mRaw = spoken.groupValues[2].ifEmpty { spoken.groupValues[5] }.toIntOrNull() ?: 0
        val period = spoken.groupValues[3].ifEmpty { spoken.groupValues[6] }
        var h = hRaw
        if ((period == "tarde" || period == "noite") && h in 1..11) h += 12
        if (period == "manha" && h == 12) h = 0
        if (h !in 0..23 || mRaw !in 0..59) return null
        return String.format("%02d:%02d", h, mRaw)
    }

    private fun findMentionedSection(
        userInput: String,
        configuredSections: List<SectionRuleConfig>
    ): String? {
        val normInput = stripAccents(userInput)
        return configuredSections.firstOrNull { sec ->
            val normSec = stripAccents(sec.name.trim())
            normSec.length >= 3 && !normSec.equals("geral", ignoreCase = true) &&
                Regex("""\b${Regex.escape(normSec)}\b""").containsMatchIn(normInput)
        }?.name
    }

    /**
     * Usa a transcrição do Whisper (`small`) como âncora para o título:
     * Se o Qwen 0.5B alucinar um título que não tem relação com o que o Whisper ouviu,
     * usa o título limpo direto da fala do Whisper.
     */
    internal fun anchorTitleToWhisperInput(slmTitle: String, rawUserInput: String): String {
        val whisperTitle = cleanSpokenTitle(rawUserInput)
        if (whisperTitle.length < 3) return slmTitle
        val slmWords = stripAccents(slmTitle).split(Regex("""[^a-z0-9]+""")).filter { it.length >= 3 }
        val inputWords = stripAccents(rawUserInput).split(Regex("""[^a-z0-9]+""")).filter { it.length >= 3 }.toSet()
        val hasOverlap = slmWords.any { it in inputWords }
        return if (hasOverlap && slmTitle.length in 3..80) slmTitle else whisperTitle
    }

    internal fun cleanSpokenTitle(userInput: String): String {
        var cleanTitle = userInput.trim()
            .replace(Regex("""^(?iu)(?:al[oô]|oi|opa|teste|testando|gravando|ei\s+buddy)[\s,.:;-]+"""), "")
            .replace(Regex("""(?iu)(?:^|\s)(?:depois\s+de\s+amanh[aã]|amanh[aã]|hoje)(?=\s|$|[.,;:])"""), " ")
            .replace(Regex("""(?iu)(?:^|\s)(?:[aà]s\s+)?\d{1,2}(?::\d{2})?\s*(?:h(?:oras?)?)?(?:\s+da\s+(?:manh[aã]|tarde|noite))?(?=\s|$|[.,;:])"""), " ")
            .replace(Regex("""(?iu)(?:^|\s)da\s+(?:manh[aã]|tarde|noite)(?=\s|$|[.,;:])"""), " ")
            .replace(Regex("""^(?iu)\s*(?:eu\s+quero|quero|eu\s+vou|vou|preciso|tenho\s+que|lembrar\s+de)\s+"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', ',', '.', '-', ';', ':')

        if (cleanTitle.length < 3) {
            cleanTitle = userInput.trim()
                .replace(Regex("""^(?iu)(?:al[oô]|teste|testando)[\s,.:;-]+"""), "")
                .trim()
        }
        return cleanTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private val fillerStopWords = setOf(
        "teste", "testando", "alo", "oi", "ola", "opa", "ei", "buddy", "hm", "hum",
        "um", "dois", "tres", "1", "2", "3", "som", "microfone", "gravando", "ok",
        "ta", "bom", "obrigado", "valeu", "nada", "so", "isso", "ah", "eh"
    )

    /**
     * Se o modelo 0.5B marcar erroneamente como INVALIDO uma frase que tem conteúdo real
     * (ex.: "Amanhã às 8 da manhã ir pra faculdade" ou "teste eu vou pra faculdade amanhã"),
     * resgata automaticamente como um lembrete `- [ ]` com data, horário e notificação extraídos!
     */
    fun rescueFalseInvalidIfNeeded(
        userInput: String,
        decision: DecisionPayload,
        configuredSections: List<SectionRuleConfig>,
        today: LocalDate = LocalDate.now()
    ): DecisionPayload {
        if (decision.isValidDecision && decision.operations.isNotEmpty()) {
            return decision
        }

        val normalized = Normalizer.normalize(userInput.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
        val tokens = normalized.split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
        val meaningfulTokens = tokens.filter { it !in fillerStopWords && it.length >= 2 }

        // Só permanece INVALIDO se realmente não houver nenhuma palavra de conteúdo real
        if (meaningfulTokens.size < 2) {
            return decision
        }

        val extractedDeadline = extractExplicitDeadlineFromText(userInput, today)
        val extractedTime = extractExplicitTimeFromText(userInput)
        val cleanTitle = cleanSpokenTitle(userInput)

        val defaultSection = configuredSections.firstOrNull()?.name ?: "Geral"
        val matchedSection = findMentionedSection(userInput, configuredSections) ?: defaultSection

        val hasSchedule = extractedDeadline != null || extractedTime != null
        // Se tem horário/data, notifica 30 minutos antes (30); se não tem data nem hora, sem notificação (0)
        val notifyBeforeMin = if (hasSchedule) 30 else 0
        val priority = if (extractedDeadline == today.toString()) "P1" else if (hasSchedule) "P2" else "P3"

        val rescuedReasoning = buildString {
            append("Lembrete identificado pelo Whisper: \"$cleanTitle\"")
            if (extractedDeadline != null) append(" para $extractedDeadline")
            if (extractedTime != null) append(" às $extractedTime (notificar 30min antes)")
            append(".")
        }

        return DecisionPayload(
            reasoning = rescuedReasoning,
            status = "VALIDO",
            invalid_reason = null,
            operations = listOf(
                TicketOperation(
                    op = "create",
                    section = matchedSection,
                    priority = priority,
                    title = cleanTitle,
                    deadline = extractedDeadline,
                    time = extractedTime,
                    reminder_hours = notifyBeforeMin,
                    notes = ""
                )
            )
        )
    }
}
