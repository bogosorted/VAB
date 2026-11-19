package com.aragao.vab.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aragao.vab.core.markdown.SectionRuleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.vabDataStore by preferencesDataStore(name = "vab_settings")

data class VabSettings(
    val sections: List<SectionRuleConfig> = defaultSections(),
    val personalContext: String = DEFAULT_PERSONAL_CONTEXT,
    val floatingBubbleEnabled: Boolean = false,
    val externalSafTreeUri: String? = null,
    val customSlmModelPath: String? = null,
    val customWhisperModelPath: String? = null
) {
    companion object {
        const val DEFAULT_PERSONAL_CONTEXT =
            "Sou estudante universitário. Priorize entregas e provas com prazo próximo como P1, tarefas importantes da semana como P2 e lembretes gerais sem urgência como P3."

        fun defaultSections(): List<SectionRuleConfig> = listOf(
            SectionRuleConfig(
                name = "Urgente",
                description = "Atividades críticas para hoje, amanhã ou com prazo estourando."
            ),
            SectionRuleConfig(
                name = "Faculdade",
                description = "Trabalhos, relatórios, provas, listas de exercícios e assuntos acadêmicos."
            ),
            SectionRuleConfig(
                name = "Pessoal",
                description = "Compras, saúde, casa, compromissos pessoais e rotina do dia a dia."
            ),
            SectionRuleConfig(
                name = "Ideias & Notas",
                description = "Anotações sintetizadas de contexto, referências ou ideias futuras sem prazo."
            )
        )
    }
}

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val SECTIONS_JSON: Preferences.Key<String> = stringPreferencesKey("sections_json")
        val PERSONAL_CONTEXT: Preferences.Key<String> = stringPreferencesKey("personal_context")
        val BUBBLE_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("floating_bubble_enabled")
        val SAF_TREE_URI: Preferences.Key<String> = stringPreferencesKey("external_saf_tree_uri")
        val CUSTOM_SLM_PATH: Preferences.Key<String> = stringPreferencesKey("custom_slm_path")
        val CUSTOM_WHISPER_PATH: Preferences.Key<String> = stringPreferencesKey("custom_whisper_path")
    }

    val settingsFlow: Flow<VabSettings> = context.vabDataStore.data.map { prefs ->
        val sections = prefs[Keys.SECTIONS_JSON]?.let { raw ->
            runCatching { json.decodeFromString<List<SectionRuleConfig>>(raw) }.getOrNull()
        }?.takeIf { it.isNotEmpty() } ?: VabSettings.defaultSections()

        VabSettings(
            sections = sections,
            personalContext = prefs[Keys.PERSONAL_CONTEXT] ?: VabSettings.DEFAULT_PERSONAL_CONTEXT,
            floatingBubbleEnabled = prefs[Keys.BUBBLE_ENABLED] ?: false,
            externalSafTreeUri = prefs[Keys.SAF_TREE_URI]?.takeIf { it.isNotBlank() },
            customSlmModelPath = prefs[Keys.CUSTOM_SLM_PATH]?.takeIf { it.isNotBlank() },
            customWhisperModelPath = prefs[Keys.CUSTOM_WHISPER_PATH]?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun getSnapshot(): VabSettings = settingsFlow.first()

    suspend fun updateSections(sections: List<SectionRuleConfig>) {
        val clean = sections
            .map { it.copy(name = it.name.trim(), description = it.description.trim()) }
            .filter { it.name.isNotEmpty() }
            .distinctBy { it.name.lowercase() }
            .ifEmpty { VabSettings.defaultSections() }

        context.vabDataStore.edit { prefs ->
            prefs[Keys.SECTIONS_JSON] = json.encodeToString(clean)
        }
    }

    suspend fun updatePersonalContext(text: String) {
        context.vabDataStore.edit { prefs ->
            prefs[Keys.PERSONAL_CONTEXT] = text
        }
    }

    suspend fun setFloatingBubbleEnabled(enabled: Boolean) {
        context.vabDataStore.edit { prefs ->
            prefs[Keys.BUBBLE_ENABLED] = enabled
        }
    }

    suspend fun setExternalSafTreeUri(uriString: String?) {
        context.vabDataStore.edit { prefs ->
            if (uriString.isNullOrBlank()) {
                prefs.remove(Keys.SAF_TREE_URI)
            } else {
                prefs[Keys.SAF_TREE_URI] = uriString
            }
        }
    }

    suspend fun setCustomSlmModelPath(path: String?) {
        context.vabDataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(Keys.CUSTOM_SLM_PATH)
            } else {
                prefs[Keys.CUSTOM_SLM_PATH] = path
            }
        }
    }
}
