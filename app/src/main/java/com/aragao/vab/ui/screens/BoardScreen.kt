package com.aragao.vab.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aragao.vab.core.buddy.BuddyDna
import com.aragao.vab.core.buddy.BuddyVisualState
import com.aragao.vab.core.markdown.MarkdownBoard
import com.aragao.vab.core.markdown.Priority
import com.aragao.vab.core.markdown.Ticket
import com.aragao.vab.data.queue.InvalidInputNotice
import com.aragao.vab.data.queue.QueueItemEntity
import com.aragao.vab.data.queue.QueueStatus
import com.aragao.vab.ui.components.BuddyPixelCanvas
import com.aragao.vab.ui.theme.VabPalette
import android.media.MediaPlayer
import java.io.File
import java.time.LocalDate

@Composable
fun BoardScreen(
    buddyDna: BuddyDna,
    buddyVisualState: BuddyVisualState,
    audioAmplitude: Float,
    isRecordingVoice: Boolean,
    isTypingInApp: Boolean,
    activeQueueCount: Int,
    board: MarkdownBoard,
    rawMarkdown: String,
    queueItems: List<QueueItemEntity>,
    latestInvalidNotice: InvalidInputNotice?,
    onDismissInvalidNotice: () -> Unit,
    onOpenQueueTab: () -> Unit,
    onToggleTicket: (String) -> Unit,
    onDeleteTicket: (String) -> Unit,
    onUpdateTicketSchedule: (String, String?, String?, Int) -> Unit,
    onAddSectionTab: (String, String) -> Unit,
    onRemoveSectionTab: (String) -> Unit,
    onSetRawEditorLocked: (Boolean) -> Unit,
    onSaveRawMarkdown: (String) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onEnqueueQuickText: (String) -> Unit,
    onToggleVoiceRecord: () -> Unit
) {
    var isRawMode by remember { mutableStateOf(false) }
    var draftMarkdown by remember { mutableStateOf(rawMarkdown) }
    var quickInput by remember { mutableStateOf("") }
    var isQuickFocused by remember { mutableStateOf(false) }
    var showDecisionBanner by remember { mutableStateOf(true) }
    var selectedSectionName by remember { mutableStateOf("Geral") }
    var showAddTabDialog by remember { mutableStateOf(false) }
    var newTabName by remember { mutableStateOf("") }
    var newTabHint by remember { mutableStateOf("") }
    var editingScheduleTicket by remember { mutableStateOf<Ticket?>(null) }

    val todayIso = remember { LocalDate.now().toString() }

    val sections = board.sections
    LaunchedEffect(sections) {
        if (sections.isNotEmpty() && sections.none { it.name.equals(selectedSectionName, ignoreCase = true) }) {
            selectedSectionName = sections.first().name
        }
    }

    val activeSection = sections.firstOrNull { it.name.equals(selectedSectionName, ignoreCase = true) }
        ?: sections.firstOrNull()

    val latestDecidedItem = remember(queueItems) {
        queueItems.firstOrNull {
            it.status in listOf(QueueStatus.APPLIED, QueueStatus.INVALID) &&
                (it.reasoningText.isNotBlank() || it.appliedSummary.isNotBlank())
        }
    }

    LaunchedEffect(rawMarkdown, isRawMode) {
        if (!isRawMode) {
            draftMarkdown = rawMarkdown
        }
    }

    LaunchedEffect(isQuickFocused, quickInput, isRawMode, showAddTabDialog) {
        onTypingChanged(isRawMode || showAddTabDialog || (isQuickFocused && quickInput.isNotBlank()))
    }

    DisposableEffect(isRawMode) {
        onSetRawEditorLocked(isRawMode)
        onDispose {
            onTypingChanged(false)
            if (isRawMode) {
                onSetRawEditorLocked(false)
            }
        }
    }

    if (editingScheduleTicket != null) {
        val target = editingScheduleTicket!!
        var deadlineDraft by remember(target.id) { mutableStateOf(target.deadline.orEmpty()) }
        var timeDraft by remember(target.id) { mutableStateOf(target.time.orEmpty()) }
        var reminderDraft by remember(target.id) { mutableStateOf(target.reminderHours) }

        AlertDialog(
            onDismissRequest = { editingScheduleTicket = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Data, Horário e Lembrete",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = deadlineDraft,
                        onValueChange = { deadlineDraft = it },
                        label = { Text("Data (YYYY-MM-DD ou vazio)") },
                        placeholder = { Text("Ex: $todayIso") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                deadlineDraft = todayIso
                                if (reminderDraft == 0) reminderDraft = 30
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("Hoje", style = MaterialTheme.typography.labelSmall) }
                        OutlinedButton(
                            onClick = {
                                deadlineDraft = LocalDate.now().plusDays(1).toString()
                                if (reminderDraft == 0) reminderDraft = 30
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("Amanhã", style = MaterialTheme.typography.labelSmall) }
                        OutlinedButton(
                            onClick = {
                                deadlineDraft = ""
                                timeDraft = ""
                                reminderDraft = 0
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("Limpar", style = MaterialTheme.typography.labelSmall) }
                    }

                    OutlinedTextField(
                        value = timeDraft,
                        onValueChange = {
                            timeDraft = it
                            if (it.isNotBlank() && reminderDraft == 0) reminderDraft = 30
                        },
                        label = { Text("Horário (HH:mm ou vazio)") },
                        placeholder = { Text("Ex: 08:00") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Notificar quanto tempo ANTES do horário/data?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val options = listOf(
                        0 to "🔕 Sem not.",
                        15 to "🔔 15min antes",
                        30 to "🔔 30min antes",
                        60 to "🔔 1h antes",
                        1440 to "🔔 1d antes"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { (minutesBefore, label) ->
                                    val selected = reminderDraft == minutesBefore
                                    Surface(
                                        color = if (selected) VabPalette.TerracottaSoft else VabPalette.MutedSurface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) VabPalette.Terracotta else VabPalette.CardBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { reminderDraft = minutesBefore }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) VabPalette.Terracotta else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanDate = deadlineDraft.trim().takeIf { it.isNotEmpty() }
                        val cleanTime = timeDraft.trim().takeIf { it.isNotEmpty() }
                        val finalReminder = if (cleanDate == null && cleanTime == null && reminderDraft == 0) 0 else reminderDraft
                        onUpdateTicketSchedule(target.id, cleanDate, cleanTime, finalReminder)
                        editingScheduleTicket = null
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingScheduleTicket = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showAddTabDialog) {
        AlertDialog(
            onDismissRequest = { showAddTabDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Nova aba no fichário",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTabName,
                        onValueChange = { newTabName = it },
                        label = { Text("Nome da aba (ex: Faculdade, Ideias)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newTabHint,
                        onValueChange = { newTabHint = it },
                        label = { Text("Dica pro Buddy (o que entra aqui?)") },
                        placeholder = { Text("Ex: Provas, trabalhos e matérias") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newTabName.trim()
                        if (clean.isNotEmpty()) {
                            onAddSectionTab(clean, newTabHint.trim())
                            selectedSectionName = clean
                            newTabName = ""
                            newTabHint = ""
                            showAddTabDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Criar aba")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTabDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Header com o Buddy + barra de abas estilo Tilix / Fichário
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = VabPalette.MutedSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, VabPalette.CardBorder),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Seu Buddy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subtitle = when {
                            isRecordingVoice -> "Ouvindo e anotando no caderno..."
                            isTypingInApp -> "Anotando o que você está escrevendo..."
                            activeQueueCount > 0 -> "Organizando $activeQueueCount nota(s) no fichário..."
                            buddyVisualState == BuddyVisualState.IDLE_STRESSED_P1 -> "Tem prioridade P1 vencendo hoje 👀"
                            else -> "De boa por aqui • pronto pra anotar"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isRecordingVoice) VabPalette.P1Red else MaterialTheme.colorScheme.onSurfaceVariant
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
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, VabPalette.CardBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isRawMode) Icons.Default.ViewAgenda else Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRawMode) "Fichário" else "Ver .md",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Barra de abas estilo Tilix / Fichário com botão [ + ]
                if (!isRawMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sections.forEach { sec ->
                            val isSelected = activeSection?.name.equals(sec.name, ignoreCase = true)
                            val openCount = sec.tickets.count { !it.completed }

                            Surface(
                                color = if (isSelected) VabPalette.TerracottaSoft else VabPalette.MutedSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) VabPalette.Terracotta else VabPalette.CardBorder
                                ),
                                modifier = Modifier.clickable { selectedSectionName = sec.name }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = sec.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) VabPalette.Terracotta else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (openCount > 0) {
                                        Surface(
                                            color = if (isSelected) VabPalette.Terracotta else VabPalette.CardBorder,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = openCount.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) VabPalette.CardSurface else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (isSelected && sections.size > 1 && sec.tickets.isEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Fechar aba vazia",
                                            tint = VabPalette.Terracotta,
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable { onRemoveSectionTab(sec.name) }
                                        )
                                    }
                                }
                            }
                        }

                        // Botãozinho [ + ] tipo Tilix para abrir uma nova aba no fichário
                        Surface(
                            color = VabPalette.CardSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, VabPalette.CardBorder),
                            modifier = Modifier.clickable { showAddTabDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Nova aba",
                                    tint = VabPalette.JiraBlue,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                if (latestDecidedItem != null && !isRawMode) {
                    Surface(
                        color = VabPalette.MutedSurface.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDecisionBanner = !showDecisionBanner }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (latestDecidedItem.status == QueueStatus.INVALID) {
                                        VabPalette.P1Red
                                    } else {
                                        VabPalette.JiraBlue
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (latestDecidedItem.status == QueueStatus.INVALID) {
                                        "Buddy pulou: ${latestDecidedItem.invalidReason}"
                                    } else {
                                        "Última do Buddy: ${latestDecidedItem.appliedSummary}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = if (showDecisionBanner) 4 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showDecisionBanner) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            AnimatedVisibility(visible = showDecisionBanner) {
                                Column(modifier = Modifier.padding(top = 6.dp)) {
                                    if (latestDecidedItem.reasoningText.isNotBlank()) {
                                        Text(
                                            text = "Como ele pensou: ${latestDecidedItem.reasoningText}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = onOpenQueueTab) {
                                            Text("Ver nos Bastidores")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = VabPalette.CardBorder)
            }
        }

        if (latestInvalidNotice != null && !isRawMode) {
            Surface(
                color = VabPalette.P1RedSoft,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VabPalette.P1Red.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = VabPalette.P1Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hmm, o Buddy não achou tarefa nisso",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = VabPalette.P1Red
                        )
                        Text(
                            text = "\"${latestInvalidNotice.inputText}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = latestInvalidNotice.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismissInvalidNotice,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar aviso",
                            tint = VabPalette.P1Red,
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
                        color = VabPalette.WarningAmberBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, VabPalette.P2Amber.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = "O Buddy está segurando $activeQueueCount nota(s) até você salvar o Markdown para não atropelar sua edição.",
                            color = VabPalette.WarningAmberText,
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
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
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
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, VabPalette.CardBorder)
                    ) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onSaveRawMarkdown(draftMarkdown)
                            isRawMode = false
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar .md")
                    }
                }
            }
        } else {
            // Página ativa do Fichário
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activeSection != null) {
                    items(activeSection.freeformLines) { freeLine ->
                        Text(
                            text = freeLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    if (activeSection.tickets.isEmpty() && activeSection.freeformLines.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, VabPalette.CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Aba \"${activeSection.name}\" limpinha",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Manda um áudio ou escreve ali embaixo que o Buddy anota e organiza pra você.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(activeSection.tickets, key = { it.id }) { ticket ->
                            val originItem = remember(queueItems, ticket.id, ticket.title) {
                                queueItems.firstOrNull { item ->
                                    item.status == QueueStatus.APPLIED && (
                                        item.appliedSummary.contains("(${ticket.id})") ||
                                            item.appliedSummary.contains("\"${ticket.title}\"", ignoreCase = true) ||
                                            (ticket.title.length >= 4 && item.rawDecisionJson.contains(ticket.title, ignoreCase = true))
                                        )
                                }
                            }
                            TicketItemCard(
                                ticket = ticket,
                                todayIso = todayIso,
                                originItem = originItem,
                                onToggle = { onToggleTicket(ticket.id) },
                                onDelete = { onDeleteTicket(ticket.id) },
                                onEditSchedule = { editingScheduleTicket = ticket }
                            )
                        }
                    }
                }
            }

            // Barra inferior de captura rápida
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = VabPalette.CardBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickInput,
                            onValueChange = { quickInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    isQuickFocused = focusState.isFocused
                                },
                            placeholder = { Text("Manda uma tarefa, prazo ou ideia...") },
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp)
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
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Mandar pro Buddy")
                            }
                        } else {
                            FilledIconButton(
                                onClick = onToggleVoiceRecord,
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isRecordingVoice) {
                                        VabPalette.P1Red
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Falar com o Buddy"
                                )
                            }
                        }
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
    originItem: QueueItemEntity?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEditSchedule: () -> Unit
) {
    val (prioFg, prioBg) = when (ticket.priority) {
        Priority.P1 -> VabPalette.P1Red to VabPalette.P1RedSoft
        Priority.P2 -> VabPalette.P2Amber to VabPalette.P2AmberSoft
        Priority.P3 -> VabPalette.JiraBlue to VabPalette.JiraBlueSoft
    }

    val isOverdueOrToday = !ticket.completed && ticket.deadline != null && ticket.deadline <= todayIso
    var showOriginBox by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(ticket.id) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }

    Surface(
        color = if (ticket.completed) VabPalette.MutedSurface.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, VabPalette.CardBorder),
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
                        color = prioBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = ticket.priority.name,
                            color = prioFg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = if (ticket.deadline != null || ticket.time != null) {
                            if (isOverdueOrToday) VabPalette.P1RedSoft else VabPalette.JiraBlueSoft
                        } else {
                            VabPalette.MutedSurface
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onEditSchedule() }
                    ) {
                        val datePart = ticket.deadline?.let { "📅 $it" }
                        val timePart = ticket.time?.let { "⏰ $it" }
                        val label = listOfNotNull(datePart, timePart).joinToString(" ").ifEmpty { "📅 Sem data" }
                        Text(
                            text = label,
                            color = if (ticket.deadline != null || ticket.time != null) {
                                if (isOverdueOrToday) VabPalette.P1Red else VabPalette.JiraBlue
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = if (ticket.reminderHours > 0) VabPalette.TerracottaSoft else VabPalette.MutedSurface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onEditSchedule() }
                    ) {
                        val remLabel = when {
                            ticket.reminderHours <= 0 -> "🔕 Sem not."
                            ticket.reminderHours % 1440 == 0 -> "🔔 ${ticket.reminderHours / 1440}d antes"
                            ticket.reminderHours % 60 == 0 -> "🔔 ${ticket.reminderHours / 60}h antes"
                            else -> "🔔 ${ticket.reminderHours}min antes"
                        }
                        Text(
                            text = remLabel,
                            color = if (ticket.reminderHours > 0) VabPalette.Terracotta else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (originItem != null) {
                        val hasAudio = !originItem.audioFilePath.isNullOrBlank()
                        Surface(
                            color = if (showOriginBox) VabPalette.JiraBlueSoft else VabPalette.MutedSurface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { showOriginBox = !showOriginBox }
                        ) {
                            Text(
                                text = if (hasAudio) "🎤 Origem" else "💬 Origem",
                                color = if (showOriginBox) VabPalette.JiraBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
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
                    color = if (ticket.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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

                // Origem do ticket (- [ ]): áudio original + o que o Whisper ouviu + reasoning do Qwen
                AnimatedVisibility(visible = showOriginBox && originItem != null) {
                    if (originItem != null) {
                        val audioFile = originItem.audioFilePath?.let(::File)?.takeIf { it.exists() }
                        Surface(
                            color = VabPalette.MutedSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, VabPalette.CardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (audioFile != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                if (isPlayingAudio) {
                                                    runCatching {
                                                        mediaPlayer?.stop()
                                                        mediaPlayer?.release()
                                                    }
                                                    mediaPlayer = null
                                                    isPlayingAudio = false
                                                } else {
                                                    runCatching {
                                                        mediaPlayer?.release()
                                                        val mp = MediaPlayer().apply {
                                                            setDataSource(audioFile.absolutePath)
                                                            prepare()
                                                            setOnCompletionListener {
                                                                isPlayingAudio = false
                                                                it.release()
                                                                mediaPlayer = null
                                                            }
                                                            start()
                                                        }
                                                        mediaPlayer = mp
                                                        isPlayingAudio = true
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isPlayingAudio) "⏹ Parar áudio" else "▶ Ouvir áudio gravado",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "👂 O que o Buddy entendeu (Whisper): \"${originItem.contentText}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (originItem.reasoningText.isNotBlank()) {
                                    Text(
                                        text = "🧠 Reasoning do Qwen: ${originItem.reasoningText}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
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
