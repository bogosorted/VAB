package com.aragao.vab

import com.aragao.vab.core.ai.GbnfGrammarBuilder
import com.aragao.vab.core.buddy.BuddyGenerator
import com.aragao.vab.core.buddy.BuddyVisualState
import com.aragao.vab.core.markdown.BoardPatchEngine
import com.aragao.vab.core.markdown.DecisionPayload
import com.aragao.vab.core.markdown.MarkdownParser
import com.aragao.vab.core.markdown.Priority
import com.aragao.vab.core.markdown.SectionRuleConfig
import com.aragao.vab.core.markdown.TicketOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VabCoreTest {

    @Test
    fun `markdown parser and serializer roundtrip preserves sections tickets notes and freeform lines`() {
        val sampleMd = """
            ## Urgente

            Nota manual livre do usuário no topo da seção.

            - [ ] **[P1]** Entregar relatório de Física 📅 2026-11-20 <!-- id:t_09 -->
              > Focar nos gráficos do experimento 3; vale 2 pontos.

            ## Faculdade

            - [x] **[P3]** Enviar lista 3 <!-- id:t_04 -->
            - [ ] Tarefa digitada manualmente sem tags
        """.trimIndent()

        val board = MarkdownParser.parse(sampleMd)
        assertEquals(2, board.sections.size)
        assertEquals("Urgente", board.sections[0].name)
        assertEquals(listOf("Nota manual livre do usuário no topo da seção."), board.sections[0].freeformLines)

        val urgentTicket = board.sections[0].tickets.single()
        assertEquals("t_09", urgentTicket.id)
        assertEquals(Priority.P1, urgentTicket.priority)
        assertEquals("2026-11-20", urgentTicket.deadline)
        assertEquals("Entregar relatório de Física", urgentTicket.title)
        assertEquals("Focar nos gráficos do experimento 3; vale 2 pontos.", urgentTicket.notes)

        // Tarefa digitada manualmente ganha prioridade P2 padrão e sobe acima do item concluído [x] ao serializar
        val serialized = MarkdownParser.serialize(board)
        val reparsed = MarkdownParser.parse(serialized)
        assertEquals(2, reparsed.sections[1].tickets.size)
        assertFalse(reparsed.sections[1].tickets[0].completed)
        assertTrue(reparsed.sections[1].tickets[1].completed)
    }

    @Test
    fun `patch engine creates updates completes and sorts by priority and deadline`() {
        val sections = listOf(
            SectionRuleConfig("Urgente", "Críticos"),
            SectionRuleConfig("Faculdade", "Aulas")
        )
        val initial = MarkdownParser.parse("", sections.map { it.name })

        val createDecision = DecisionPayload(
            operations = listOf(
                TicketOperation(
                    op = "create",
                    section = "Faculdade",
                    priority = "P2",
                    title = "Estudar Redes",
                    deadline = "2026-11-25",
                    notes = "Capítulos 3 e 4"
                ),
                TicketOperation(
                    op = "create",
                    section = "Faculdade",
                    priority = "P1",
                    title = "Prova de Cálculo",
                    deadline = "2026-11-18",
                    notes = "Integrais duplas"
                )
            )
        )

        val step1 = BoardPatchEngine.applyDecision(initial, createDecision, sections)
        val facTickets = step1.updatedBoard.sections.first { it.name == "Faculdade" }.tickets
        assertEquals(2, facTickets.size)
        // P1 deve vir primeiro automaticamente
        assertEquals("Prova de Cálculo", facTickets[0].title)
        assertTrue(step1.updatedBoard.hasUrgentDueOrOverdueP1("2026-11-18"))

        // Conclui o P1 e atualiza o P2 para Urgente
        val p1Id = facTickets[0].id
        val p2Id = facTickets[1].id
        val updateDecision = DecisionPayload(
            operations = listOf(
                TicketOperation(op = "complete", target_id = p1Id),
                TicketOperation(
                    op = "update",
                    target_id = p2Id,
                    section = "Urgente",
                    priority = "P1",
                    deadline = "2026-11-22",
                    notes = "Professor antecipou a cobrança"
                )
            )
        )

        val step2 = BoardPatchEngine.applyDecision(step1.updatedBoard, updateDecision, sections)
        val urgTickets = step2.updatedBoard.sections.first { it.name == "Urgente" }.tickets
        assertEquals(1, urgTickets.size)
        assertEquals("Estudar Redes", urgTickets[0].title)
        assertEquals(Priority.P1, urgTickets[0].priority)
        assertEquals("2026-11-22", urgTickets[0].deadline)
        // Em 2026-11-18, o P1 para 2026-11-22 ainda não venceu hoje
        assertFalse(step2.updatedBoard.hasUrgentDueOrOverdueP1("2026-11-18"))
    }

    @Test
    fun `dynamic gbnf restricts sections and only exposes update or complete when open tickets exist`() {
        val emptyBoardGbnf = GbnfGrammarBuilder.build(
            sectionNames = listOf("Urgente", "TCC"),
            openTicketIds = emptyList()
        )
        assertTrue(emptyBoardGbnf.contains("operation ::= create-op"))
        assertFalse(emptyBoardGbnf.contains("complete-op"))
        assertTrue(emptyBoardGbnf.contains("TCC"))

        val populatedGbnf = GbnfGrammarBuilder.build(
            sectionNames = listOf("Urgente", "TCC"),
            openTicketIds = listOf("t_01", "t_02")
        )
        assertTrue(populatedGbnf.contains("operation ::= create-op | update-op | complete-op"))
        assertTrue(populatedGbnf.contains("t_01"))
        assertTrue(populatedGbnf.contains("t_02"))
    }

    @Test
    fun `buddy procedural generator is deterministic symmetric on chill idle and unique per device`() {
        val dnaA1 = BuddyGenerator.generateDna("android-device-abc123")
        val dnaA2 = BuddyGenerator.generateDna("android-device-abc123")
        val dnaB = BuddyGenerator.generateDna("android-device-xyz987")

        assertEquals(dnaA1, dnaA2)
        assertNotEquals(dnaA1.seed, dnaB.seed)

        val chillFrame = BuddyGenerator.renderFrame(dnaA1, BuddyVisualState.IDLE_CHILL, tick = 0)
        assertEquals(256, chillFrame.size)

        // Verifica simetria horizontal do sprite 16x16 no estado IDLE_CHILL
        for (y in 0 until BuddyGenerator.GRID_SIZE) {
            for (x in 0 until BuddyGenerator.GRID_SIZE / 2) {
                val left = chillFrame[y * 16 + x]
                val right = chillFrame[y * 16 + (15 - x)]
                assertEquals("Pixel assimétrico em ($x, $y)", left, right)
            }
        }

        val listeningFrame = BuddyGenerator.renderFrame(
            dnaA1,
            BuddyVisualState.LISTENING,
            tick = 0,
            audioAmplitude = 0.8f
        )
        assertFalse(chillFrame.contentEquals(listeningFrame))
    }
}
