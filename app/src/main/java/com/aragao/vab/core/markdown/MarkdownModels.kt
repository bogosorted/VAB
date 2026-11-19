package com.aragao.vab.core.markdown

import kotlinx.serialization.Serializable

@Serializable
enum class Priority(val rank: Int) {
    P1(1),
    P2(2),
    P3(3);

    companion object {
        fun fromString(raw: String?): Priority = when (raw?.trim()?.uppercase()) {
            "P1" -> P1
            "P3" -> P3
            else -> P2
        }
    }
}

@Serializable
data class SectionRuleConfig(
    val name: String,
    val description: String
)

@Serializable
data class Ticket(
    val id: String,
    val title: String,
    val priority: Priority = Priority.P2,
    val completed: Boolean = false,
    val deadline: String? = null,
    val time: String? = null,
    val reminderHours: Int = 0,
    val notes: String = ""
)

data class BoardSection(
    val name: String,
    val tickets: List<Ticket> = emptyList(),
    val freeformLines: List<String> = emptyList()
)

data class MarkdownBoard(
    val preambleLines: List<String> = emptyList(),
    val sections: List<BoardSection> = emptyList()
) {
    fun openTicketsWithSection(): List<Pair<String, Ticket>> {
        return sections.flatMap { sec ->
            sec.tickets.filter { !it.completed }.map { ticket -> sec.name to ticket }
        }
    }

    fun hasUrgentDueOrOverdueP1(todayIso: String): Boolean {
        return sections.any { sec ->
            sec.tickets.any { ticket ->
                !ticket.completed &&
                    ticket.priority == Priority.P1 &&
                    (ticket.deadline == null || ticket.deadline <= todayIso)
            }
        }
    }
}

@Serializable
data class DecisionPayload(
    val reasoning: String = "",
    val status: String = "VALIDO",
    val invalid_reason: String? = null,
    val operations: List<TicketOperation> = emptyList()
) {
    val isValidDecision: Boolean
        get() = !status.trim().equals("INVALIDO", ignoreCase = true)
}

@Serializable
data class TicketOperation(
    val op: String, // "create", "update", "complete"
    val section: String? = null,
    val priority: String? = null,
    val title: String? = null,
    val deadline: String? = null,
    val time: String? = null,
    val reminder_hours: Int? = null,
    val notes: String? = null,
    val target_id: String? = null
)
