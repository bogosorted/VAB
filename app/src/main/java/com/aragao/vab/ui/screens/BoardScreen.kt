package com.aragao.vab.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aragao.vab.core.buddy.BuddyDna
import com.aragao.vab.core.buddy.BuddyVisualState
import com.aragao.vab.core.markdown.BoardSection
import com.aragao.vab.core.markdown.MarkdownBoard
import com.aragao.vab.core.markdown.Priority
import com.aragao.vab.core.markdown.Ticket
import com.aragao.vab.ui.components.BuddyPixelCanvas
import java.time.LocalDate

@Composable
fun BoardScreen(
    buddyDna: BuddyDna,
    buddyVisualState: BuddyVisualState,
    audioAmplitude: Float,
    isRecordingVoice: Boolean,
    activeQueueCount: Int,
    board: MarkdownBoard,
    rawMarkdown: String,
    onToggleTicket: (String) -> Unit,
    onDeleteTicket: (String) -> Unit,
    onSetRawEditorLocked: (Boolean) -> Unit,
    onSaveRawMarkdown: (String) -> Unit,
    onEnqueueQuickText: (String) -> Unit,
    onToggleVoiceRecord: () -> Unit
) {
    var isRawMode by remember { mutableStateOf(false) }
    var draftMarkdown by remember { mutableStateOf(rawMarkdown) }
    var quickInput by remember { mutableStateOf("") }
    val collapsedSections = remember { mutableStateMapOf<String, Boolean>() }
    val todayIso = remember { LocalDate.now().toString() }

    LaunchedEffect(rawMarkdown, isRawMode) {
        if (!isRawMode) {
            draftMarkdown = rawMarkdown
        }
    }

    DisposableEffect(isRawMode) {
        onSetRawEditorLocked(isRawMode)
        onDispose {
            if (isRawMode) {
                onSetRawEditorLocked(false)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header com o Buddy Tamagotchi + Status + Switch Dual-View (Cards <-> Markdown)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BuddyPixelCanvas(
                        dna = buddyDna,
                        state = buddyVisualState,
                        audioAmplitude = audioAmplitude,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VAB • Quadro Mental",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = when {
                        isRecordingVoice -> "Gravando voz... toque para enfileirar"
                        activeQueueCount > 0 -> "Processando $activeQueueCount item(ns) na fila..."
                        buddyVisualState == BuddyVisualState.IDLE_STRESSED_P1 -> "Atenção: há P1 urgente ou vencendo!"
                        else -> "Em dia • ${buddyDna.code}"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRecordingVoice) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (isRawMode) {
                            onSaveRawMarkdown(draftMarkdown)
                            isRawMode = false
                        } else {
                            draftMarkdown = rawMarkdown
                            isRawMode = true
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isRawMode) Icons.Default.ViewAgenda else Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isRawMode) "Ver Cards" else "Editar .md")
                }
            }
        }

        if (isRawMode) {
            // Modo Editor Markdown Cru
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                if (activeQueueCount > 0) {
                    Surface(
                        color = Color(0xFF3A2E14),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = "Há $activeQueueCount item(ns) na fila aguardando você salvar o Markdown para não sobrescrever sua edição.",
                            color = Color(0xFFFFD580),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = draftMarkdown,
                    onValueChange = { draftMarkdown = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    ),
                    label = { Text("vab_board.md (Fonte da Verdade)") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            draftMarkdown = rawMarkdown
                            onSetRawEditorLocked(false)
                            isRawMode = false
                        }
                    ) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onSaveRawMarkdown(draftMarkdown)
                            isRawMode = false
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar .md")
                    }
                }
            }
        } else {
            // Modo Seções e Cards Sincronizados
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(board.sections, key = { it.name }) { section ->
                    val isCollapsed = collapsedSections[section.name] == true
                    SectionCardGroup(
                        section = section,
                        todayIso = todayIso,
                        isCollapsed = isCollapsed,
                        onToggleCollapse = {
                            collapsedSections[section.name] = !isCollapsed
                        },
                        onToggleTicket = onToggleTicket,
                        onDeleteTicket = onDeleteTicket
                    )
                }
            }

            // Barra inferior rápida de captura (voz em 1 toque + texto rápido)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quickInput,
                        onValueChange = { quickInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Despeje uma pendência para a fila...") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (quickInput.isNotBlank()) {
                        FilledIconButton(
                            onClick = {
                                onEnqueueQuickText(quickInput)
                                quickInput = ""
                            },
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Enviar para fila")
                        }
                    } else {
                        FilledIconButton(
                            onClick = onToggleVoiceRecord,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isRecordingVoice) Color(0xFFFF4D4D) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Gravar voz para fila"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCardGroup(
    section: BoardSection,
    todayIso: String,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleTicket: (String) -> Unit,
    onDeleteTicket: (String) -> Unit
) {
    val openCount = section.tickets.count { !it.completed }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCollapse() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "## ${section.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "$openCount abertos",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    section.freeformLines.forEach { freeLine ->
                        Text(
                            text = freeLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (section.tickets.isEmpty() && section.freeformLines.isEmpty()) {
                        Text(
                            text = "Nenhum ticket nesta seção.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    section.tickets.forEach { ticket ->
                        TicketItemCard(
                            ticket = ticket,
                            todayIso = todayIso,
                            onToggle = { onToggleTicket(ticket.id) },
                            onDelete = { onDeleteTicket(ticket.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketItemCard(
    ticket: Ticket,
    todayIso: String,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val prioColor = when (ticket.priority) {
        Priority.P1 -> Color(0xFFFF5C5C)
        Priority.P2 -> Color(0xFFFFB74D)
        Priority.P3 -> Color(0xFF4DD0E1)
    }

    val isOverdueOrToday = !ticket.completed && ticket.deadline != null && ticket.deadline <= todayIso

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (ticket.completed) 0.45f else 0.9f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = ticket.completed,
                onCheckedChange = { onToggle() }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = prioColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = ticket.priority.name,
                            color = prioColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (ticket.deadline != null) {
                        Surface(
                            color = if (isOverdueOrToday) Color(0xFF4A1F24) else Color(0xFF1F2A3A),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "📅 ${ticket.deadline}",
                                color = if (isOverdueOrToday) Color(0xFFFF8A80) else Color(0xFF90CAF9),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = ticket.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (ticket.completed) TextDecoration.LineThrough else TextDecoration.None
                )

                if (ticket.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ticket.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Excluir ticket",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
