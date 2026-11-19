package com.aragao.vab.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aragao.vab.core.ai.ModelRuntimeStatus
import com.aragao.vab.data.queue.QueueInputType
import com.aragao.vab.data.queue.QueueItemEntity
import com.aragao.vab.data.queue.QueueStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Motor Local (Whisper + Qwen GBNF)",
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Liberar RAM", style = MaterialTheme.typography.labelSmall)
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
                text = "Fila de Requisições & Histórico (${items.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (items.any { it.status in listOf(QueueStatus.APPLIED, QueueStatus.UNDONE, QueueStatus.FAILED) }) {
                TextButton(onClick = onClearHistory) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpar concluídos")
                }
            }
        }

        if (items.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = "A fila está vazia. Toque no seu Buddy flutuante (ou no microfone da tela inicial) para narrar pendências sem interromper o que estiver fazendo.",
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
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.createdAtEpochMs))
    val (statusLabel, statusColor) = when (item.status) {
        QueueStatus.PENDING -> "NA FILA" to Color(0xFF90CAF9)
        QueueStatus.TRANSCRIBING -> "TRANSCREVENDO VOZ..." to Color(0xFFCE93D8)
        QueueStatus.DECIDING -> "DECIDINDO PRIORIDADES (QWEN)..." to Color(0xFFFFD54F)
        QueueStatus.WAITING_EDITOR -> "AGUARDANDO SALVAR .MD" to Color(0xFFFFB74D)
        QueueStatus.APPLIED -> "APLICADO NO .MD" to Color(0xFF81C784)
        QueueStatus.UNDONE -> "DESFEITO (UNDO)" to Color(0xFF9EA7B3)
        QueueStatus.FAILED -> "FALHA" to Color(0xFFFF6B6B)
        else -> item.status to Color.LightGray
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
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
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (item.appliedSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "→ ${item.appliedSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA5D6A7)
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (item.status == QueueStatus.APPLIED && item.previousMarkdownSnapshot != null) {
                    OutlinedButton(
                        onClick = onUndo,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Desfazer (Undo)", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (item.status in listOf(QueueStatus.FAILED, QueueStatus.UNDONE)) {
                    OutlinedButton(
                        onClick = onReprocess,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reprocessar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
