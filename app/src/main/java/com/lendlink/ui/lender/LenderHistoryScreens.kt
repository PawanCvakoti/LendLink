package com.lendlink.ui.lender

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lendlink.data.model.LenderCreditHistory
import com.lendlink.data.model.LendHistory
import com.lendlink.ui.common.fmtDate
import com.lendlink.ui.common.fmtDateTime
import com.lendlink.ui.common.krw
import com.lendlink.ui.theme.AvailGreen
import com.lendlink.ui.theme.LentOrange
import com.lendlink.viewmodel.LenderViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── CREDIT HISTORY ────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LenderCreditHistoryScreen(vm: LenderViewModel, onBack: () -> Unit) {
    val history by vm.creditHistory.collectAsState()
    val wallet by vm.wallet.collectAsState()
    val totalBorrow = history.filter { it.type == "borrow_payment" }.sumOf { it.amount }
    val totalPenalty = history.filter { it.type == "penalty" }.sumOf { it.amount }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Credit History") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            // Summary card
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Total Balance", fontSize = 11.sp, color = Color.White.copy(.7f), letterSpacing = 1.sp)
                    Text(krw(wallet), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text("From borrows", fontSize = 10.sp, color = Color.White.copy(.6f))
                            Text(krw(totalBorrow), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        Column {
                            Text("From penalties", fontSize = 10.sp, color = Color.White.copy(.6f))
                            Text(krw(totalPenalty), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }

            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(.25f))
                        Spacer(Modifier.height(12.dp))
                        Text("No credit history yet", color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                        Text("Credits appear when borrowers rent your items",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(.4f))
                    }
                }
            } else {
                val grouped = history.groupBy { try { dayLabel(it.timestamp) } catch(_: Exception) { "Other" } }
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    grouped.forEach { (day, entries) ->
                        item {
                            Text(day, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(.5f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        items(entries, key = { it.entryId.ifEmpty { UUID.randomUUID().toString() } }) { entry ->
                            CreditEntryRow(entry)
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditEntryRow(entry: LenderCreditHistory) {
    val isBorrow = entry.type == "borrow_payment"
    val iconTint = if (isBorrow) AvailGreen else LentOrange
    val icon = if (isBorrow) Icons.Default.ArrowDownward else Icons.Default.Warning

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape)
            .background(iconTint.copy(.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(fmtDateTime(entry.timestamp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(.5f))
        }
        Spacer(Modifier.width(8.dp))
        Text("+${krw(entry.amount)}", fontWeight = FontWeight.Bold, color = iconTint, fontSize = 14.sp)
    }
}

// ── LEND HISTORY ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendHistoryScreen(vm: LenderViewModel, onBack: () -> Unit) {
    val history by vm.lendHistory.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Lend History") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { pad ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(.25f))
                    Spacer(Modifier.height(12.dp))
                    Text("No completed lends yet", color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    Text("Returned items appear here", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(.4f))
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp,
                start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("${history.size} completed lend${if (history.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    Spacer(Modifier.height(4.dp))
                }
                items(history, key = { it.historyId.ifEmpty { UUID.randomUUID().toString() } }) { record ->
                    LendHistoryCard(record)
                }
            }
        }
    }
}

@Composable
private fun LendHistoryCard(record: LendHistory) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            if (record.itemImageUrl.isNotEmpty()) {
                AsyncImage(model = record.itemImageUrl, contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.itemName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(record.itemCategory.ifBlank { "General" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    Spacer(Modifier.width(4.dp))
                    Text(record.borrowerName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(.7f))
                }
                if (record.borrowerPhone.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    Spacer(Modifier.width(4.dp))
                    Text(record.borrowerPhone, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                }
                if (record.borrowerLocation.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(.5f))
                    Spacer(Modifier.width(4.dp))
                    Text(record.borrowerLocation, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Lent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(.4f))
                        Text(fmtDate(record.lentAt), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    Column {
                        Text("Returned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(.4f))
                        Text(fmtDate(record.returnedAt), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.background(AvailGreen.copy(.12f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("Returned", style = MaterialTheme.typography.labelSmall, color = AvailGreen, fontWeight = FontWeight.SemiBold)
                    }
                    Text(krw(record.price), style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun dayLabel(ts: Long): String {
    val cal = Calendar.getInstance(); val today = Calendar.getInstance()
    cal.timeInMillis = ts
    return try {
        when {
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
            else -> SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(ts))
        }
    } catch (e: Exception) {
        "Previous"
    }
}
