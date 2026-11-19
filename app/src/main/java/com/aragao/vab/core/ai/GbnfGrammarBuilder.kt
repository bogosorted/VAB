package com.aragao.vab.core.ai

object GbnfGrammarBuilder {

    /**
     * Compila em tempo real uma gramática GBNF estrita com:
     * 1. Campo "reasoning" gerado PRIMEIRO (Chain-of-Thought) antes de qualquer decisão.
     * 2. Classificação "status" em "VALIDO" ou "INVALIDO":
     *    - Só libera "operations" quando status == "VALIDO".
     *    - Quando "INVALIDO", trava "operations": [] e exige "invalid_reason".
     * 3. Enum estrito das Seções configuradas, prioridades P1..P3, datas ISO e IDs reais abertos.
     */
    fun build(
        sectionNames: List<String>,
        openTicketIds: List<String>
    ): String {
        val safeSections = sectionNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("Geral") }

        val sectionAlts = safeSections.joinToString(" | ") { sec ->
            "\"\\\"${escapeGbnfLiteral(sec)}\\\"\""
        }

        val cleanIds = openTicketIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val hasExistingTickets = cleanIds.isNotEmpty()
        val operationRule = if (hasExistingTickets) {
            "create-op | update-op | complete-op"
        } else {
            "create-op"
        }

        val targetIdRule = if (hasExistingTickets) {
            val idAlts = cleanIds.joinToString(" | ") { id ->
                "\"\\\"${escapeGbnfLiteral(id)}\\\"\""
            }
            "target-id ::= $idAlts\n"
        } else {
            ""
        }

        val existingOpsRules = if (hasExistingTickets) {
            """
            update-op ::= "{" ws "\"op\"" ws ":" ws "\"update\"" ws "," ws "\"target_id\"" ws ":" ws target-id ws "," ws "\"section\"" ws ":" ws section-enum ws "," ws "\"priority\"" ws ":" ws priority-enum ws "," ws "\"deadline\"" ws ":" ws deadline-val ws "," ws "\"time\"" ws ":" ws time-val ws "," ws "\"reminder_hours\"" ws ":" ws reminder-val ws "," ws "\"notes\"" ws ":" ws note-str ws "}"
            complete-op ::= "{" ws "\"op\"" ws ":" ws "\"complete\"" ws "," ws "\"target_id\"" ws ":" ws target-id ws "}"
            """.trimIndent()
        } else {
            ""
        }

        return buildString {
            appendLine("""root ::= "{" ws "\"reasoning\"" ws ":" ws reasoning-str ws "," ws decision-body ws "}" ws""")
            appendLine("""decision-body ::= valid-body | invalid-body""")
            appendLine("""valid-body ::= "\"status\"" ws ":" ws "\"VALIDO\"" ws "," ws "\"invalid_reason\"" ws ":" ws "null" ws "," ws "\"operations\"" ws ":" ws "[" ws op-list ws "]" """)
            appendLine("""invalid-body ::= "\"status\"" ws ":" ws "\"INVALIDO\"" ws "," ws "\"invalid_reason\"" ws ":" ws reason-str ws "," ws "\"operations\"" ws ":" ws "[]" """)
            appendLine("""op-list ::= operation (ws "," ws operation){0,5}""")
            appendLine("""operation ::= $operationRule""")
            appendLine(
                """create-op ::= "{" ws "\"op\"" ws ":" ws "\"create\"" ws "," ws "\"section\"" ws ":" ws section-enum ws "," ws "\"priority\"" ws ":" ws priority-enum ws "," ws "\"title\"" ws ":" ws title-str ws "," ws "\"deadline\"" ws ":" ws deadline-val ws "," ws "\"time\"" ws ":" ws time-val ws "," ws "\"reminder_hours\"" ws ":" ws reminder-val ws "," ws "\"notes\"" ws ":" ws note-str ws "}""""
            )
            if (existingOpsRules.isNotEmpty()) {
                appendLine(existingOpsRules)
            }
            appendLine("""section-enum ::= $sectionAlts""")
            appendLine("""priority-enum ::= "\"P1\"" | "\"P2\"" | "\"P3\""""")
            if (targetIdRule.isNotEmpty()) {
                append(targetIdRule)
            }
            appendLine("""deadline-val ::= "null" | ("\"20" [2-9] [0-9] "-" ("0" [1-9] | "1" [0-2]) "-" ("0" [1-9] | [1-2] [0-9] | "3" [0-1]) "\"")""")
            appendLine("""time-val ::= "null" | ("\"" ([0-1] [0-9] | "2" [0-3]) ":" [0-5] [0-9] "\"")""")
            appendLine("""reminder-val ::= "0" | "15" | "30" | "60" | "1440"""")
            appendLine("""reasoning-str ::= "\"" safe-char{10,220} "\"" """)
            appendLine("""reason-str ::= "\"" safe-char{8,140} "\"" """)
            appendLine("""title-str ::= "\"" safe-char{2,110} "\"" """)
            appendLine("""note-str ::= "\"" safe-char{0,220} "\"" """)
            appendLine("""safe-char ::= [^"\\\x7F\x00-\x1F] | "\\" (["\\bfnrt] | "u" [0-9a-fA-F]{4})""")
            appendLine("""ws ::= | " " | "\n" [ \t]{0,8}""")
        }
    }

    internal fun escapeGbnfLiteral(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                '\\' -> sb.append("\\\\\\\\")
                '"' -> sb.append("\\\\\\\"")
                '\n', '\r', '\t' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
