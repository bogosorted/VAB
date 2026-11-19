package com.aragao.vab.core.ai

object GbnfGrammarBuilder {

    /**
     * Compila em tempo real uma gramática GBNF estrita contendo:
     * - Apenas os nomes exatos das Seções configuradas pelo usuário.
     * - Apenas prioridades P1, P2 ou P3.
     * - Datas estritamente no formato ISO YYYY-MM-DD ou null.
     * - Em update/complete, apenas IDs reais de tickets abertos existentes (evitando 100% de IDs alucinados).
     */
    fun build(
        sectionNames: List<String>,
        openTicketIds: List<String>
    ): String {
        val safeSections = sectionNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("Urgente", "Faculdade", "Pessoal") }

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
            update-op ::= "{" ws "\"op\"" ws ":" ws "\"update\"" ws "," ws "\"target_id\"" ws ":" ws target-id ws "," ws "\"section\"" ws ":" ws section-enum ws "," ws "\"priority\"" ws ":" ws priority-enum ws "," ws "\"deadline\"" ws ":" ws deadline-val ws "," ws "\"notes\"" ws ":" ws note-str ws "}"
            complete-op ::= "{" ws "\"op\"" ws ":" ws "\"complete\"" ws "," ws "\"target_id\"" ws ":" ws target-id ws "}"
            """.trimIndent()
        } else {
            ""
        }

        return buildString {
            appendLine("""root ::= "{" ws "\"operations\"" ws ":" ws "[" ws op-list ws "]" ws "}" ws""")
            appendLine("""op-list ::= operation (ws "," ws operation){0,5}""")
            appendLine("""operation ::= $operationRule""")
            appendLine(
                """create-op ::= "{" ws "\"op\"" ws ":" ws "\"create\"" ws "," ws "\"section\"" ws ":" ws section-enum ws "," ws "\"priority\"" ws ":" ws priority-enum ws "," ws "\"title\"" ws ":" ws title-str ws "," ws "\"deadline\"" ws ":" ws deadline-val ws "," ws "\"notes\"" ws ":" ws note-str ws "}""""
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
