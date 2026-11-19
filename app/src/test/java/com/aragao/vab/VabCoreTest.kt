package com.aragao.vab

import com.aragao.vab.core.ai.GbnfGrammarBuilder
import com.aragao.vab.core.ai.PromptBuilder
import com.aragao.vab.core.buddy.BuddyGenerator
import com.aragao.vab.core.buddy.BuddyVisualState
import com.aragao.vab.core.markdown.BoardPatchEngine
import com.aragao.vab.core.markdown.DecisionPayload
import com.aragao.vab.core.markdown.MarkdownParser
import com.aragao.vab.core.markdown.Priority
import com.aragao.vab.core.markdown.SectionRuleConfig
import com.aragao.vab.core.markdown.TicketOperation
import com.aragao.vab.whisper.AudioPreProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sin

/**
 * Suite de Especificacoes Executaveis (STDD — Specification & Test-Driven Development).
 *
 * Cada teste documenta e verifica um contrato arquitetural imutavel do VAB:
 *  - [SPEC-01] Parser e Serializer Markdown bidirecional sem perdas (lossless round-trip)
 *  - [SPEC-02] Motor de Decisoes (BoardPatchEngine: create, update, complete, ordenacao P1..P3 + Deadline)
 *  - [SPEC-03] Compilador de Gramatica GBNF Dinamica (Constrained Decoding sem alucinacao de secoes ou IDs)
 *  - [SPEC-04] Montador de Prompt Contextual (PromptBuilder com calendario relativo de 7 dias e indice enxuto)
 *  - [SPEC-05] Gerador Procedural Tamagotchi 16x16 (Seed ANDROID_ID imutavel, simetria, contorno 1px e humor P1)
 */
class VabCoreTest {

    @Test
    fun `SPEC-01 markdown parser and serializer roundtrip preserves sections tickets notes and freeform lines`() {
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
        assertEquals(Priority.P2, reparsed.sections[1].tickets[0].priority)
        assertTrue(reparsed.sections[1].tickets[1].completed)
    }

    @Test
    fun `SPEC-01 empty markdown initializes fallback configured sections`() {
        val board = MarkdownParser.parse("", listOf("Urgente", "Faculdade", "Pessoal"))
        assertEquals(3, board.sections.size)
        assertEquals(listOf("Urgente", "Faculdade", "Pessoal"), board.sections.map { it.name })
    }

    @Test
    fun `SPEC-02 patch engine creates updates completes and sorts by priority and deadline`() {
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
    fun `SPEC-02 patch engine sanitizes invalid deadlines and falls back to default section`() {
        val sections = listOf(SectionRuleConfig("Geral", "Padrao"))
        val initial = MarkdownParser.parse("", listOf("Geral"))

        val decision = DecisionPayload(
            operations = listOf(
                TicketOperation(
                    op = "create",
                    section = "SecaoInexistente",
                    priority = "P3",
                    title = "Comprar caderno",
                    deadline = "sexta-feira", // formato inválido deve virar null sem quebrar
                    notes = ""
                )
            )
        )

        val outcome = BoardPatchEngine.applyDecision(initial, decision, sections)
        val ticket = outcome.updatedBoard.sections.first().tickets.single()
        assertEquals("Comprar caderno", ticket.title)
        assertNull(ticket.deadline)
    }

    @Test
    fun `SPEC-03 dynamic gbnf restricts sections and only exposes update or complete when open tickets exist`() {
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
        assertTrue(populatedGbnf.contains("reasoning-str"))
        assertTrue(populatedGbnf.contains("valid-body"))
        assertTrue(populatedGbnf.contains("invalid-body"))
        assertTrue(populatedGbnf.contains("VALIDO"))
        assertTrue(populatedGbnf.contains("INVALIDO"))
        assertTrue(populatedGbnf.contains("reminder_hours"))
        assertTrue(populatedGbnf.contains("reminder-val"))
    }

    @Test
    fun `SPEC-02 patch engine ignores board edits when decision status is INVALIDO`() {
        val sections = listOf(SectionRuleConfig("Geral", "Padrao"))
        val initial = MarkdownParser.parse("", listOf("Geral"))
        val invalidPayload = DecisionPayload(
            reasoning = "Texto é apenas teste de áudio sem tarefa.",
            status = "INVALIDO",
            invalid_reason = "Nenhuma pendência detectada",
            operations = listOf(
                TicketOperation(op = "create", section = "Geral", priority = "P1", title = "Nao criar")
            )
        )
        assertFalse(invalidPayload.isValidDecision)
        val outcome = BoardPatchEngine.applyDecision(initial, invalidPayload, sections)
        assertTrue(outcome.appliedDescriptions.isEmpty())
        assertTrue(outcome.updatedBoard.sections.first().tickets.isEmpty())

        // Porém, se o modelo 0.5B marcar erroneamente como INVALIDO um lembrete real
        // ("Amanhã às 8 da manhã ir pra faculdade"), o rescueFalseInvalidIfNeeded resgata em - [ ] com data e hora!
        val falseInvalidFromSmallModel = DecisionPayload(
            reasoning = "Sem ticket aberto.",
            status = "INVALIDO",
            invalid_reason = "Sem tarefa ou ticket aberto",
            operations = emptyList()
        )
        val rescued = BoardPatchEngine.rescueFalseInvalidIfNeeded(
            userInput = "Amanhã às 8 da manhã ir pra faculdade",
            decision = falseInvalidFromSmallModel,
            configuredSections = sections,
            today = LocalDate.of(2026, 11, 18)
        )
        assertTrue(rescued.isValidDecision)
        val rescuedOutcome = BoardPatchEngine.applyDecision(initial, rescued, sections)
        val rescuedTicket = rescuedOutcome.updatedBoard.sections.first().tickets.single()
        assertEquals("Ir pra faculdade", rescuedTicket.title)
        assertEquals("2026-11-19", rescuedTicket.deadline)
        assertEquals("08:00", rescuedTicket.time)
        // Notifica 30 minutos antes (e não de 2 em 2 horas)
        assertEquals(30, rescuedTicket.reminderHours)

        // E se o usuário falar apenas "quero ir pra faculdade" (sem horário nem data) e o Qwen 0.5B tentar
        // alucinar "08:00", o BoardPatchEngine bloqueia a alucinação de horário/data!
        val hallucinatedTimeDecision = DecisionPayload(
            reasoning = "Ir para a faculdade",
            status = "VALIDO",
            operations = listOf(
                TicketOperation(
                    op = "create",
                    section = "Geral",
                    priority = "P2",
                    title = "Ir para a faculdade",
                    deadline = "2026-11-19",
                    time = "08:00",
                    reminder_hours = 30
                )
            )
        )
        val cleanOutcome = BoardPatchEngine.applyDecision(
            board = initial,
            decision = hallucinatedTimeDecision,
            configuredSections = sections,
            rawUserInput = "quero ir pra faculdade",
            today = LocalDate.of(2026, 11, 18)
        )
        val cleanTicket = cleanOutcome.updatedBoard.sections.first().tickets.single()
        assertNull("Não deve inventar data quando o usuário não falou data", cleanTicket.deadline)
        assertNull("Não deve inventar horário 08:00 quando o usuário não falou horário", cleanTicket.time)
        assertEquals("Sem data/horário não deve ter notificação", 0, cleanTicket.reminderHours)
    }

    @Test
    fun `SPEC-04 prompt builder includes 7-day relative calendar and open ticket index excluding completed items`() {
        val sections = listOf(
            SectionRuleConfig("Faculdade", "Disciplinas e provas")
        )
        val sysPrompt = PromptBuilder.buildSystemPrompt(
            sections = sections,
            userPersonalContext = "Estudante de Computação",
            today = LocalDate.of(2026, 11, 18)
        )
        assertTrue(sysPrompt.contains("2026-11-18"))
        assertTrue(sysPrompt.contains("2026-11-19"))
        assertTrue(sysPrompt.contains("Faculdade"))
        assertTrue(sysPrompt.contains("VALIDO"))
        assertTrue(sysPrompt.contains("INVALIDO"))
        assertTrue(sysPrompt.contains("reasoning"))

        val md = """
            ## Faculdade
            - [ ] **[P1]** Entregar TP de Compiladores 📅 2026-11-20 <!-- id:t_open -->
            - [x] **[P2]** Lista antiga concluída <!-- id:t_done -->
        """.trimIndent()
        val board = MarkdownParser.parse(md)
        val userPrompt = PromptBuilder.buildUserPrompt("Já terminei o TP de Compiladores", board)

        assertTrue(userPrompt.contains("t_open"))
        assertFalse(userPrompt.contains("t_done"))
    }

    @Test
    fun `SPEC-05 buddy procedural generator is deterministic symmetric on chill idle and unique per device`() {
        val dnaA1 = BuddyGenerator.generateDna("android-device-abc123")
        val dnaA2 = BuddyGenerator.generateDna("android-device-abc123")
        val dnaB = BuddyGenerator.generateDna("android-device-xyz987")

        assertEquals(dnaA1, dnaA2)
        assertNotEquals(dnaA1.seed, dnaB.seed)
        assertTrue(dnaA1.code.startsWith("VAB-"))

        val chillFrame = BuddyGenerator.renderFrame(dnaA1, BuddyVisualState.IDLE_CHILL, tick = 0)
        val grid = BuddyGenerator.GRID_SIZE
        assertEquals(16, grid)
        assertEquals(grid * grid, chillFrame.size)

        // Verifica simetria horizontal do sprite 16x16 no estado IDLE_CHILL
        for (y in 0 until grid) {
            for (x in 0 until grid / 2) {
                val left = chillFrame[y * grid + x]
                val right = chillFrame[y * grid + (grid - 1 - x)]
                assertEquals("Pixel assimétrico em ($x, $y)", left, right)
            }
        }

        val listeningFrame = BuddyGenerator.renderFrame(
            dnaA1,
            BuddyVisualState.LISTENING,
            tick = 0,
            audioAmplitude = 0.8f
        )
        val stressedFrame = BuddyGenerator.renderFrame(
            dnaA1,
            BuddyVisualState.IDLE_STRESSED_P1,
            tick = 0
        )
        val notingFrame = BuddyGenerator.renderFrame(dnaA1, BuddyVisualState.NOTING, tick = 1)
        val organizingFrame = BuddyGenerator.renderFrame(dnaA1, BuddyVisualState.ORGANIZING, tick = 1)
        val curiousFrame = BuddyGenerator.renderFrame(dnaA1, BuddyVisualState.IDLE_CURIOUS, tick = 1)
        val playfulFrame = BuddyGenerator.renderFrame(dnaA1, BuddyVisualState.IDLE_PLAYFUL, tick = 1)
        val sleepyFrame = BuddyGenerator.renderFrame(dnaA1, BuddyVisualState.IDLE_SLEEPY, tick = 1)

        assertFalse(chillFrame.contentEquals(listeningFrame))
        assertFalse(chillFrame.contentEquals(stressedFrame))
        assertFalse(chillFrame.contentEquals(notingFrame))
        assertFalse(chillFrame.contentEquals(organizingFrame))
        assertFalse(notingFrame.contentEquals(organizingFrame))
        assertFalse(curiousFrame.contentEquals(sleepyFrame))
        assertFalse(playfulFrame.contentEquals(sleepyFrame))
    }

    @Test
    fun `SPEC-06 audio preprocessor removes silence normalizes quiet speech and drops pure noise`() {
        val pureSilence = FloatArray(16000) { 0.0005f }
        assertTrue(AudioPreProcessor.preprocessForWhisper(pureSilence).isEmpty())

        val tooShort = FloatArray(800) { 0.2f }
        assertTrue(AudioPreProcessor.preprocessForWhisper(tooShort).isEmpty())

        // 1s silêncio + 0.5s fala baixa com DC offset + 1s silêncio
        val raw = FloatArray(40000) { idx ->
            val dc = 0.03f
            if (idx in 16000 until 24000) {
                dc + 0.04f * sin(2.0 * Math.PI * 220.0 * idx / 16000.0).toFloat()
            } else {
                dc
            }
        }
        val processed = AudioPreProcessor.preprocessForWhisper(raw, 16000)
        assertTrue("Áudio recortado deve ser menor que o original", processed.size in 8000..17000)
        val maxPeak = processed.maxOf { abs(it) }
        assertTrue("Fala baixa deve ser normalizada para perto de 0.75 (atual=$maxPeak)", maxPeak > 0.35f)
    }
}
