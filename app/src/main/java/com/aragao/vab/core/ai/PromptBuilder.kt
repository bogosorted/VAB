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

        val defaultSection = sections.firstOrNull()?.name ?: "Geral"
        val todayIso = today.toString()
        val tomorrowIso = today.plusDays(1).toString()

        return """
            Você é o Buddy do VAB. Sua missão é anotar lembretes, compromissos, aulas, horários, tarefas e ideias do usuário no fichário Markdown (`- [ ]`).
            Pense em "reasoning" ANTES de preencher "status" e "operations".

            REGRA DE OURO ("status"):
            - Quase tudo é "VALIDO"! Qualquer lembrete ("amanhã às 8 da manhã ir pra faculdade"), compromisso, horário, tarefa, estudo, compra ou nota DEVE ser "VALIDO" com op="create", MESMO quando não existir nenhum ticket aberto.
            - Se a frase tiver a palavra "teste" ou "alô" junto com um lembrete (ex: "teste eu vou pra faculdade amanhã"), IGNORE a palavra "teste" e marque "VALIDO".
            - Só marque "INVALIDO" se a frase INTEIRA for 100% vazia de sentido (ex: apenas "alô 1 2", "oi", ruído puro).

            Como criar ou atualizar ("operations"):
            - Use as palavras que o usuário realmente falou para o "title" (ex: se falou "quero ir pra faculdade", o título é "Ir para a faculdade").
            - NUNCA invente "deadline" ou "time" se o usuário não falou dia nem hora! Se não falou dia, use "deadline":null. Se não falou hora, use "time":null.
            - "reminder_hours": quantos MINUTOS ANTES notificar (0 = sem notificação, 15 = 15min antes, 30 = 30min antes, 60 = 1h antes, 1440 = 1 dia antes).
              * Se tiver horário ou data, use 30 (notifica 30 minutos antes do compromisso, nunca depois).
              * Se NÃO tiver data nem horário, use 0 (sem notificação).
            - Se o usuário mencionar o nome de uma seção/aba (ex: "Faculdade"), coloque nessa seção.

            Calendário de referência:
            $calendarCheatSheet

            Contexto pessoal do usuário:
            $contextBlock

            Seções disponíveis:
            $sectionsBlock

            Exemplos de saída JSON:
            - Sem data nem hora ("quero ir pra faculdade"): {"reasoning":"O usuário quer anotar ir para a faculdade, sem data ou horário específico.","status":"VALIDO","invalid_reason":null,"operations":[{"op":"create","section":"$defaultSection","priority":"P2","title":"Ir para a faculdade","deadline":null,"time":null,"reminder_hours":0,"notes":""}]}
            - Com data e horário explícito ("amanhã às 14:30 reunião de grupo"): {"reasoning":"Reunião agendada para amanhã ($tomorrowIso) às 14:30, notificar 30 minutos antes.","status":"VALIDO","invalid_reason":null,"operations":[{"op":"create","section":"$defaultSection","priority":"P2","title":"Reunião de grupo","deadline":"$tomorrowIso","time":"14:30","reminder_hours":30,"notes":""}]}
            - Apenas ruído sem ação ("alô som 1 2"): {"reasoning":"Apenas teste de som sem nenhum lembrete ou nota.","status":"INVALIDO","invalid_reason":"Apenas teste de microfone sem lembrete.","operations":[]}

            Responda APENAS com o objeto JSON válido.
        """.trimIndent()
    }

    fun buildUserPrompt(
        userInputText: String,
        board: MarkdownBoard
    ): String {
        val openTickets = board.openTicketsWithSection()
        val openTicketsBlock = if (openTickets.isEmpty()) {
            "Quadro atual: vazio (use op=\"create\" para criar o novo lembrete/tarefa)."
        } else {
            "Tickets abertos no quadro:\n" + openTickets.take(30).joinToString("\n") { (sectionName, t) ->
                "${t.id} | $sectionName | ${t.priority.name} | ${t.title} | prazo=${t.deadline ?: "null"} ${t.time ?: ""}".trim()
            }
        }

        return """
            Pedido do usuário para anotar no fichário:
            "${userInputText.trim()}"

            $openTicketsBlock
        """.trimIndent()
    }
}
