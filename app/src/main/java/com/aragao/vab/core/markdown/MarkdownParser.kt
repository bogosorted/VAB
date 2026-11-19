package com.aragao.vab.core.markdown

import kotlin.math.abs

object MarkdownParser {
    private val sectionHeaderRegex = Regex("""^##\s+(.+?)\s*$""")
    private val taskLineRegex = Regex("""^-\s*\[([ xX])]\s*(.+)$""")
    private val priorityPrefixRegex = Regex("""^\*\*\[(P[123])]\*\*\s*""")
    private val idCommentRegex = Regex("""<!--\s*id:([a-zA-Z0-9_-]+)\s*-->""")
    private val dueEmojiRegex = Regex("""📅\s*(\d{4}-\d{2}-\d{2})""")
    private val timeEmojiRegex = Regex("""⏰\s*(\d{2}:\d{2})""")
    private val reminderEmojiRegex = Regex("""🔔\s*-?(\d+)\s*(m|min|h|d)""")

    fun parse(markdown: String, fallbackSectionNames: List<String> = emptyList()): MarkdownBoard {
        val normalized = markdown.replace("\r\n", "\n")
        val lines = if (normalized.isEmpty()) emptyList() else normalized.split("\n")

        val preamble = mutableListOf<String>()
        val sections = mutableListOf<MutableSectionBuilder>()
        var currentSection: MutableSectionBuilder? = null
        var currentTicket: MutableTicketBuilder? = null
        var autoCounter = 1

        fun flushTicket() {
            val sec = currentSection
            val t = currentTicket
            if (sec != null && t != null) {
                sec.tickets.add(t.build())
            }
            currentTicket = null
        }

        for (rawLine in lines) {
            val headerMatch = sectionHeaderRegex.matchEntire(rawLine)
            if (headerMatch != null) {
                flushTicket()
                val secName = headerMatch.groupValues[1].trim()
                val nextSec = MutableSectionBuilder(secName)
                sections.add(nextSec)
                currentSection = nextSec
                continue
            }

            if (currentSection == null) {
                if (rawLine.isNotBlank() || preamble.isNotEmpty()) {
                    preamble.add(rawLine)
                }
                continue
            }

            val taskMatch = taskLineRegex.matchEntire(rawLine.trimEnd())
            if (taskMatch != null) {
                flushTicket()
                val completed = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                var remainder = taskMatch.groupValues[2].trim()

                val idMatch = idCommentRegex.find(remainder)
                val id = idMatch?.groupValues?.get(1)
                    ?: generateDeterministicId(remainder, autoCounter++)
                if (idMatch != null) {
                    remainder = remainder.removeRange(idMatch.range).trim()
                }

                val dueMatch = dueEmojiRegex.find(remainder)
                val deadline = dueMatch?.groupValues?.get(1)
                if (dueMatch != null) {
                    remainder = remainder.removeRange(dueMatch.range).trim()
                }

                val timeMatch = timeEmojiRegex.find(remainder)
                val time = timeMatch?.groupValues?.get(1)
                if (timeMatch != null) {
                    remainder = remainder.removeRange(timeMatch.range).trim()
                }

                val reminderMatch = reminderEmojiRegex.find(remainder)
                val reminderHours = if (reminderMatch != null) {
                    val amount = reminderMatch.groupValues[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val unit = reminderMatch.groupValues[2].lowercase()
                    when (unit) {
                        "m", "min" -> amount
                        "d" -> amount * 1440
                        "h" -> if (amount in listOf(15, 30)) amount else amount * 60
                        else -> amount
                    }
                } else {
                    0
                }
                if (reminderMatch != null) {
                    remainder = remainder.removeRange(reminderMatch.range).trim()
                }

                val prioMatch = priorityPrefixRegex.find(remainder)
                val priority = Priority.fromString(prioMatch?.groupValues?.get(1))
                if (prioMatch != null) {
                    remainder = remainder.removeRange(prioMatch.range).trim()
                }

                currentTicket = MutableTicketBuilder(
                    id = id,
                    title = remainder.ifBlank { " Pendência sem título" }.trim(),
                    priority = priority,
                    completed = completed,
                    deadline = deadline,
                    time = time,
                    reminderHours = reminderHours
                )
                continue
            }

            val trimmedStart = rawLine.trimStart()
            if (currentTicket != null && trimmedStart.startsWith(">")) {
                val noteLine = trimmedStart.removePrefix(">").trimStart()
                currentTicket!!.noteLines.add(noteLine)
                continue
            }

            if (rawLine.isBlank()) {
                flushTicket()
                continue
            }

            // Linha livre escrita manualmente pelo usuário dentro da seção
            flushTicket()
            currentSection!!.freeformLines.add(rawLine)
        }
        flushTicket()

        // Garante que as seções padrão apareçam caso o documento esteja vazio
        if (sections.isEmpty() && fallbackSectionNames.isNotEmpty()) {
            fallbackSectionNames.forEach { name ->
                sections.add(MutableSectionBuilder(name))
            }
        }

        while (preamble.isNotEmpty() && preamble.last().isBlank()) {
            preamble.removeAt(preamble.lastIndex)
        }

        return MarkdownBoard(
            preambleLines = preamble,
            sections = sections.map { it.build() }
        )
    }

    fun serialize(board: MarkdownBoard, sortTickets: Boolean = true): String {
        val sb = StringBuilder()

        if (board.preambleLines.isNotEmpty()) {
            board.preambleLines.forEach { sb.appendLine(it) }
            sb.appendLine()
        }

        board.sections.forEachIndexed { idx, section ->
            sb.append("## ").appendLine(section.name.trim())
            sb.appendLine()

            section.freeformLines.forEach { line ->
                sb.appendLine(line)
            }
            if (section.freeformLines.isNotEmpty() && section.tickets.isNotEmpty()) {
                sb.appendLine()
            }

            val orderedTickets = if (sortTickets) sortSectionTickets(section.tickets) else section.tickets
            for (ticket in orderedTickets) {
                val check = if (ticket.completed) "x" else " "
                sb.append("- [").append(check).append("] **[")
                    .append(ticket.priority.name).append("]** ")
                    .append(ticket.title.replace("\n", " ").trim())

                val cleanDeadline = ticket.deadline?.trim()?.takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
                if (cleanDeadline != null) {
                    sb.append(" 📅 ").append(cleanDeadline)
                }
                val cleanTime = ticket.time?.trim()?.takeIf { it.matches(Regex("""([01]\d|2[0-3]):[0-5]\d""")) }
                if (cleanTime != null) {
                    sb.append(" ⏰ ").append(cleanTime)
                }
                if (ticket.reminderHours > 0) {
                    val tag = when {
                        ticket.reminderHours % 1440 == 0 -> "${ticket.reminderHours / 1440}d"
                        ticket.reminderHours % 60 == 0 -> "${ticket.reminderHours / 60}h"
                        else -> "${ticket.reminderHours}m"
                    }
                    sb.append(" 🔔 -").append(tag)
                }
                sb.append(" <!-- id:").append(ticket.id).appendLine(" -->")

                val cleanNotes = ticket.notes.trim()
                if (cleanNotes.isNotEmpty()) {
                    cleanNotes.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { noteLine ->
                        sb.append("  > ").appendLine(noteLine)
                    }
                }
            }

            if (idx < board.sections.lastIndex) {
                sb.appendLine()
            }
        }

        return sb.toString().trimEnd() + "\n"
    }

    fun sortSectionTickets(tickets: List<Ticket>): List<Ticket> {
        return tickets.sortedWith(
            compareBy<Ticket> { it.completed }
                .thenBy { it.priority.rank }
                .thenBy { it.deadline ?: "9999-99-99" }
        )
    }

    private fun generateDeterministicId(seedText: String, index: Int): String {
        val hash = abs((seedText + "#$index").hashCode()).toString(16).take(4).padStart(4, '0')
        return "t_$hash"
    }

    private class MutableSectionBuilder(val name: String) {
        val tickets = mutableListOf<Ticket>()
        val freeformLines = mutableListOf<String>()

        fun build() = BoardSection(
            name = name,
            tickets = tickets.toList(),
            freeformLines = freeformLines.toList()
        )
    }

    private class MutableTicketBuilder(
        val id: String,
        val title: String,
        val priority: Priority,
        val completed: Boolean,
        val deadline: String?,
        val time: String?,
        val reminderHours: Int
    ) {
        val noteLines = mutableListOf<String>()

        fun build() = Ticket(
            id = id,
            title = title,
            priority = priority,
            completed = completed,
            deadline = deadline,
            time = time,
            reminderHours = reminderHours,
            notes = noteLines.joinToString("\n")
        )
    }
}
