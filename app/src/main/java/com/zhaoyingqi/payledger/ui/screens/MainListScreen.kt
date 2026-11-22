package com.zhaoyingqi.payledger.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhaoyingqi.payledger.PayLedgerApplication
import com.zhaoyingqi.payledger.data.TransactionRecord
import com.zhaoyingqi.payledger.ui.PayLedgerViewModel
import com.zhaoyingqi.payledger.ui.formatCents

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainListScreen() {
    val app = LocalContext.current.applicationContext as PayLedgerApplication
    val vm: PayLedgerViewModel = viewModel(initializer = { PayLedgerViewModel(app.dao) })
    val groups by vm.groups.collectAsStateWithLifecycle()
    val todayCents by vm.todayCents.collectAsStateWithLifecycle()
    val monthCents by vm.monthCents.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TransactionRecord?>(null) }
    var deleting by remember { mutableStateOf<TransactionRecord?>(null) }
    var showDebug by remember { mutableStateOf(false) }

    if (showDebug) {
        DebugPreviewScreen(onBack = { showDebug = false })
        return
    }

    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            SummaryCard(todayCents, monthCents, onLongClick = { showDebug = true })
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无记录，去支付一笔试试", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    groups.forEach { group ->
                        item(key = "header-${group.date.toEpochDay()}") {
                            Text(
                                "${group.label}  支出 ${formatCents(group.totalCents)}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(group.records, key = { it.id }) { record ->
                            RecordRow(
                                record = record,
                                onClick = { editing = record },
                                onLongClick = { deleting = record },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { record ->
        NoteEditSheet(
            record = record,
            onDismiss = { editing = null },
            onSave = { note ->
                vm.updateNote(record.id, note)
                editing = null
            },
        )
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除记录") },
            text = { Text("删除 ${formatCents(record.amountCents)} 这条记录？") },
            confirmButton = {
                TextButton(onClick = { vm.delete(record.id); deleting = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SummaryCard(todayCents: Long, monthCents: Long, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SummaryItem("今日支出", todayCents)
        SummaryItem("本月支出", monthCents)
    }
}

@Composable
private fun SummaryItem(label: String, cents: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Text(formatCents(cents), style = MaterialTheme.typography.titleLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(record: TransactionRecord, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (record.channel == "WECHAT") "微信" else "支付宝",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                record.counterparty.ifEmpty { record.summary.take(20) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(formatCents(record.amountCents), style = MaterialTheme.typography.titleMedium)
        }
        if (record.note.isNotEmpty()) {
            Text(
                "备注：${record.note}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditSheet(record: TransactionRecord, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(record.id) { mutableStateOf(record.note) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("编辑备注", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("这笔钱花在了哪？") },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(text.trim()) }) { Text("保存") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
