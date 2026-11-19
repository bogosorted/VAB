package com.aragao.vab.core.ai

import com.aragao.vab.core.markdown.MarkdownBoard
import com.aragao.vab.core.markdown.SectionRuleConfig
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object PromptBuilder {

    private val ptBrLocale = Locale("pt", "BR")

    fun buildSystemPrompt(
        sections: List<SectionRuleConfig>,
        userPersonalContext: String,
        today: LocalDate = LocalDate.now()
    ): String {
        val calendarCheatSheet = (0L..7L).joinToString(" | ") { offset ->
            val d = today.plusDays(offset)
            val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, ptBrLocale)
            val label = when (offset) {
                0L -> "Hoje ($dow)"
                1L -> "Amanhã ($dow)"
                else -> dow
            }
            "$label=${d}"
        }

        val sectionsBlock = sections.joinToString("\n") { sec ->
            "- \"${sec.name}\": ${sec.description}"
        }

        val contextBlock = userPersonalContext.trim().ifEmpty {
            "Priorize entregas com prazo próximo como P1, atividades importantes da semana como P2 e lembretes gerais sem urgência como P3."
        }

        return """
            Você é o motor executivo do VAB (Virtual Assistant Buddy).
            Sua função NÃO é copiar a fala crua: você deve sintetizar títulos claros e acionáveis, extrair notas importantes nos detalhes ("notes"), escolher a seção correta, definir a prioridade (P1=alta/urgente, P2=média, P3=baixa) e converter menções de datas para YYYY-MM-DD (ou null se não houver prazo).
            Se o usuário relatar que concluiu um ticket aberto existente, use op="complete" no target_id correspondente. Se o prazo/prioridade mudou, use op="update". Caso contrário, crie 1 ou mais tickets com op="create".

            Calendário de referência:
            $calendarCheatSheet

            Contexto pessoal do usuário:
            $contextBlock

            Seções disponíveis:
            $sectionsBlock

            Responda APENAS com o objeto JSON válido de "operations".
        """.trimIndent()
    }

    fun buildUserPrompt(
        userInputText: String,
        board: MarkdownBoard
    ): String {
        val openTickets = board.openTicketsWithSection()
        val openTicketsIndex = if (openTickets.isEmpty()) {
            "(Nenhum ticket aberto no momento)"
        } else {
            openTickets.take(30).joinToString("\n") { (sectionName, t) ->
                "${t.id} | $sectionName | ${t.priority.name} | ${t.title} | prazo=${t.deadline ?: "null"}"
            }
        }

        return """
            Tickets abertos atuais:
            $openTicketsIndex

            Relato do usuário:
            "${userInputText.trim()}"
        """.trimIndent()
    }
}
