package com.aragao.vab.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aragao.vab.core.ai.ModelRuntimeStatus
import com.aragao.vab.core.markdown.DecisionPayload
import com.aragao.vab.core.markdown.TicketOperation
import com.aragao.vab.data.queue.QueueInputType
import com.aragao.vab.data.queue.QueueItemEntity
import com.aragao.vab.data.queue.QueueStatus
import com.aragao.vab.ui.theme.VabPalette
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val decisionDisplayJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Composable
fun QueueScreen(
    items: List<QueueItemEntity>,
    modelStatus: ModelRuntimeStatus,
    onUndo: (Long) -> Unit,
    onReprocess: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onForceReleaseRam: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Card de telemetria de RAM e Modelos Locais
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, VabPalette.CardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = VabPalette.JiraBlue
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cérebro do Buddy (100% Offline)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = modelStatus.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (modelStatus.slmLoadedInRam || modelStatus.whisperLoadedInRam) {
                    OutlinedButton(
                        onClick = onForceReleaseRam,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, VabPalette.CardBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Descansar", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bastidores do Buddy (${items.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (items.any {
                    it.status in listOf(
                        QueueStatus.APPLIED,
                        QueueStatus.INVALID,
                        QueueStatus.UNDONE,
                        QueueStatus.FAILED
                    )
                }
            ) {
                TextButton(onClick = onClearHistory) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpar histórico")
                }
            }
        }

        if (items.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VabPalette.CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = "Tudo quieto nos bastidores. Quando você mandar um áudio ou texto, aparece aqui o que o Buddy ouviu, como ele pensou e o que organizou no fichário.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    QueueItemCard(
                        item = item,
                        onUndo = { onUndo(item.id) },
                        onReprocess = { onReprocess(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: QueueItemEntity,
    onUndo: () -> Unit,
    onReprocess: () -> Unit
) {
    var showRawJson by remember { mutableStateOf(false) }
    val timeStr = remember(item.createdAtEpochMs) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.createdAtEpochMs))
    }

    val (statusLabel, statusFg, statusBg) = when (item.status) {
        QueueStatus.PENDING -> Triple("NA ESPERA", VabPalette.JiraBlue, VabPalette.JiraBlueSoft)
        QueueStatus.TRANSCRIBING -> Triple("OUVINDO ÁUDIO...", VabPalette.JiraBlue, VabPalette.JiraBlueSoft)
        QueueStatus.DECIDING -> Triple("PENSANDO...", VabPalette.P2Amber, VabPalette.P2AmberSoft)
        QueueStatus.WAITING_EDITOR -> Triple("ESPERANDO FECHAR O .MD", VabPalette.P2Amber, VabPalette.P2AmberSoft)
        QueueStatus.APPLIED -> Triple("ANOTADO NO FICHÁRIO", VabPalette.SuccessGreen, VabPalette.SuccessGreenSoft)
        QueueStatus.INVALID -> Triple("PULADO • SEM TAREFA", VabPalette.P1Red, VabPalette.P1RedSoft)
        QueueStatus.UNDONE -> Triple("DESFEITO", VabPalette.InkSecondary, VabPalette.MutedSurface)
        QueueStatus.FAILED -> Triple("DEU ERRO", VabPalette.P1Red, VabPalette.P1RedSoft)
        else -> Triple(item.status, VabPalette.InkSecondary, VabPalette.MutedSurface)
    }

    val parsedPayload = remember(item.rawDecisionJson) {
        if (item.rawDecisionJson.isBlank()) {
            null
        } else {
            runCatching { decisionDisplayJson.decodeFromString<DecisionPayload>(item.rawDecisionJson) }.getOrNull()
        }
    }

    val effectiveReasoning = item.reasoningText.ifBlank { parsedPayload?.reasoning.orEmpty() }
    val operations = parsedPayload?.operations.orEmpty()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, VabPalette.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusFg,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${if (item.inputType == QueueInputType.AUDIO) "🎤 Voz" else "⌨️ Texto"} • $timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.contentText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\"${item.contentText}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (effectiveReasoning.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = VabPalette.CanvasBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, VabPalette.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = VabPalette.Terracotta,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Como o Buddy pensou",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = VabPalette.Terracotta
                            )
                            Text(
                                text = effectiveReasoning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (item.status == QueueStatus.INVALID && item.invalidReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = VabPalette.P1RedSoft,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Motivo: ${item.invalidReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = VabPalette.P1Red,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (operations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "O que ele mexeu no fichário (${operations.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    operations.forEach { op ->
                        OperationDecisionChip(op = op)
                    }
                }
            } else if (item.appliedSummary.isNotBlank() && item.status != QueueStatus.INVALID) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "→ ${item.appliedSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VabPalette.SuccessGreen
                )
            }

            if (!item.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (item.rawDecisionJson.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clickable { showRawJson = !showRawJson }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = VabPalette.JiraBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showRawJson) "Ocultar JSON bruto" else "Ver JSON bruto",
                        style = MaterialTheme.typography.labelSmall,
                        color = VabPalette.JiraBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                AnimatedVisibility(visible = showRawJson) {
                    Surface(
                        color = VabPalette.MutedSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = item.rawDecisionJson,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            if ((item.status == QueueStatus.APPLIED && item.previousMarkdownSnapshot != null) ||
                item.status in listOf(QueueStatus.FAILED, QueueStatus.UNDONE, QueueStatus.INVALID)
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = VabPalette.CardBorder,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (item.status == QueueStatus.APPLIED && item.previousMarkdownSnapshot != null) {
                        OutlinedButton(
                            onClick = onUndo,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, VabPalette.CardBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Desfazer", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (item.status in listOf(QueueStatus.FAILED, QueueStatus.UNDONE, QueueStatus.INVALID)) {
                        OutlinedButton(
                            onClick = onReprocess,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, VabPalette.CardBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tentar de novo", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationDecisionChip(op: TicketOperation) {
    val actionLabel = when (op.op.trim().lowercase()) {
        "create" -> "CRIAR"
        "update" -> "ATUALIZAR"
        "complete" -> "CONCLUIR"
        else -> op.op.uppercase()
    }
    Surface(
        color = VabPalette.JiraBlueSoft.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, VabPalette.JiraBlue.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = VabPalette.JiraBlue
                )
                op.priority?.takeIf { it.isNotBlank() }?.let { prio ->
                    Text(
                        text = "[$prio]",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (prio.uppercase()) {
                            "P1" -> VabPalette.P1Red
                            "P3" -> VabPalette.JiraBlue
                            else -> VabPalette.P2Amber
                        }
                    )
                }
                op.section?.takeIf { it.isNotBlank() }?.let { sec ->
                    Text(
                        text = "• $sec",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                op.deadline?.takeIf { it.isNotBlank() && it != "null" }?.let { dl ->
                    Text(
                        text = "• 📅 $dl",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                op.target_id?.takeIf { it.isNotBlank() }?.let { tid ->
                    Text(
                        text = "($tid)",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            op.title?.takeIf { it.isNotBlank() }?.let { title ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            op.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
