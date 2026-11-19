package com.aragao.vab.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aragao.vab.core.ai.ModelRuntimeStatus
import com.aragao.vab.core.buddy.BuddyDna
import com.aragao.vab.core.buddy.BuddyVisualState
import com.aragao.vab.core.markdown.SectionRuleConfig
import com.aragao.vab.data.settings.VabSettings
import com.aragao.vab.ui.components.BuddyPixelCanvas

@Composable
fun SettingsScreen(
    buddyDna: BuddyDna,
    settings: VabSettings,
    modelStatus: ModelRuntimeStatus,
    importingModel: Boolean,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onToggleFloatingBubble: (Boolean) -> Unit,
    onUpdateSections: (List<SectionRuleConfig>) -> Unit,
    onUpdatePersonalContext: (String) -> Unit,
    onBindExternalFolder: (Uri) -> Unit,
    onUnlinkExternalFolder: () -> Unit,
    onImportCustomGguf: (Uri) -> Unit,
    onResetDefaultGguf: () -> Unit
) {
    val context = LocalContext.current
    var personalCtxDraft by remember(settings.personalContext) { mutableStateOf(settings.personalContext) }
    var newSectionName by remember { mutableStateOf("") }
    var newSectionRule by remember { mutableStateOf("") }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onBindExternalFolder(uri)
    }

    val ggufPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportCustomGguf(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1) Card do Buddy Procedural + Permissão de Botão Flutuante
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(6.dp)
                        ) {
                            BuddyPixelCanvas(
                                dna = buddyDna,
                                state = BuddyVisualState.IDLE_CHILL,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Seu Buddy Único (Tamagotchi 16x16)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "DNA Imutável: ${buddyDna.code}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Traço: ${buddyDna.headTrait.name} • Olhos: ${buddyDna.eyeStyle.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val canOverlay = Settings.canDrawOverlays(context)
                    if (!canOverlay) {
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1. Liberar Permissão de Sobreposição (Botão Flutuante)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!hasMicPermission) {
                        OutlinedButton(
                            onClick = onRequestMicPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Liberar Permissão de Microfone")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Buddy Flutuante Global",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "1 toque grava voz • Segurar abre texto rápido sobre qualquer app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.floatingBubbleEnabled && canOverlay,
                            onCheckedChange = { checked ->
                                if (checked && !Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    onToggleFloatingBubble(checked)
                                }
                            }
                        )
                    }
                }
            }
        }

        // 2) Seções Dinâmicas (compiladas direto na gramática GBNF)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Seções do Markdown & Enum GBNF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "O Qwen fica matematicamente restrito a escolher uma destas seções ao organizar seus tickets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    settings.sections.forEachIndexed { idx, sec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "## ${sec.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = sec.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (settings.sections.size > 1) {
                                IconButton(
                                    onClick = {
                                        val updated = settings.sections.filterIndexed { i, _ -> i != idx }
                                        onUpdateSections(updated)
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remover seção")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newSectionName,
                        onValueChange = { newSectionName = it },
                        label = { Text("Nova seção (ex: TCC)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newSectionRule,
                        onValueChange = { newSectionRule = it },
                        label = { Text("Regra para a IA (ex: Orientador, escrita e artigos do TCC)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (newSectionName.isNotBlank()) {
                                val updated = settings.sections + SectionRuleConfig(
                                    name = newSectionName.trim(),
                                    description = newSectionRule.trim().ifEmpty { "Assuntos de ${newSectionName.trim()}" }
                                )
                                onUpdateSections(updated)
                                newSectionName = ""
                                newSectionRule = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Adicionar Seção ao GBNF")
                    }
                }
            }
        }

        // 3) Contexto Pessoal de Priorização
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Contexto Pessoal & Regras de Prioridade",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = personalCtxDraft,
                        onValueChange = { personalCtxDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text("Instruções de rotina para calibrar P1 / P2 / P3") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onUpdatePersonalContext(personalCtxDraft) }
                    ) {
                        Text("Salvar Contexto")
                    }
                }
            }
        }

        // 4) Sincronização com Pasta Visível (Obsidian / Documentos)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sincronização Externa do vab_board.md",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (settings.externalSafTreeUri != null) {
                            "Pasta vinculada: ${settings.externalSafTreeUri}"
                        } else {
                            "Atualmente salvo no armazenamento interno. Vincule uma pasta (ex: cofre do Obsidian ou Documentos) para editar fora do app."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Escolher Pasta")
                        }
                        if (settings.externalSafTreeUri != null) {
                            OutlinedButton(onClick = onUnlinkExternalFolder) {
                                Text("Desvincular")
                            }
                        }
                    }
                }
            }
        }

        // 5) Modelos Locais (Qwen padrão embutido vs GGUF customizado)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Modelos On-Device (Whisper + Qwen)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Whisper STT: ${modelStatus.whisperModelFile?.name ?: "Aguardando modelo (.bin)"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• SLM Executivo: ${modelStatus.slmModelFile?.name ?: "Aguardando Qwen (.gguf)"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !importingModel,
                            onClick = { ggufPicker.launch(arrayOf("*/*")) }
                        ) {
                            Text(if (importingModel) "Importando .gguf..." else "Importar outro .gguf")
                        }
                        if (settings.customSlmModelPath != null) {
                            OutlinedButton(onClick = onResetDefaultGguf) {
                                Text("Usar Qwen Padrão")
                            }
                        }
                    }
                }
            }
        }
    }
}
