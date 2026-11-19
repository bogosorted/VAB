package com.aragao.vab.core.markdown

import java.util.UUID

data class PatchOutcome(
    val updatedBoard: MarkdownBoard,
    val appliedDescriptions: List<String>
)

object BoardPatchEngine {

    fun applyDecision(
        board: MarkdownBoard,
        decision: DecisionPayload,
        configuredSections: List<SectionRuleConfig>
    ): PatchOutcome {
        if (decision.operations.isEmpty()) {
            return PatchOutcome(board, emptyList())
        }

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
                    val cleanTitle = op.title?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val targetSec = resolveSectionName(op.section)
                    val prio = Priority.fromString(op.priority)
                    val deadline = sanitizeDeadline(op.deadline)
                    val newTicket = Ticket(
                        id = nextUniqueTicketId(),
                        title = cleanTitle,
                        priority = prio,
                        completed = false,
                        deadline = deadline,
                        notes = op.notes?.trim().orEmpty()
                    )
                    sectionTickets.getOrPut(targetSec) { mutableListOf() }.add(newTicket)
                    logs.add("Criado [${prio.name}] \"$cleanTitle\" em $targetSec")
                }

                "complete" -> {
                    val tid = op.target_id?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val loc = findTicketById(tid) ?: continue
                    val current = sectionTickets[loc.first]!![loc.second]
                    sectionTickets[loc.first]!![loc.second] = current.copy(completed = true)
                    logs.add("Concluído \"${current.title}\" ($tid)")
                }

                "update" -> {
                    val tid = op.target_id?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val loc = findTicketById(tid) ?: continue
                    val current = sectionTickets[loc.first]!![loc.second]

                    val updatedTicket = current.copy(
                        title = op.title?.trim()?.takeIf { it.isNotEmpty() } ?: current.title,
                        priority = if (op.priority != null) Priority.fromString(op.priority) else current.priority,
                        deadline = if (op.deadline != null) (sanitizeDeadline(op.deadline) ?: current.deadline) else current.deadline,
                        notes = op.notes?.trim()?.takeIf { it.isNotEmpty() } ?: current.notes
                    )

                    val newSec = if (!op.section.isNullOrBlank()) resolveSectionName(op.section) else loc.first
                    if (newSec != loc.first) {
                        sectionTickets[loc.first]!!.removeAt(loc.second)
                        sectionTickets.getOrPut(newSec) { mutableListOf() }.add(updatedTicket)
                        logs.add("Movido/atualizado \"${updatedTicket.title}\" para $newSec [${updatedTicket.priority.name}]")
                    } else {
                        sectionTickets[loc.first]!![loc.second] = updatedTicket
                        logs.add("Atualizado \"${updatedTicket.title}\" [${updatedTicket.priority.name}]")
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
}
