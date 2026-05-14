package com.lendlink.ui.lender

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lendlink.data.model.*
import com.lendlink.ui.common.*
import com.lendlink.ui.theme.*
import com.lendlink.viewmodel.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDamageScreen(record: BorrowRecord, vm: LenderViewModel, onBack: () -> Unit) {
    var condition by remember { mutableStateOf("Minor") }
    var description by remember { mutableStateOf("") }
    var chargeAmount by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    
    val uiState by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) {
            vm.resetUi()
            onBack()
        } else if (uiState is UiState.Error) {
            scope.launch { snack.showSnackbar((uiState as UiState.Error).message, duration = SnackbarDuration.Short) }
            vm.resetUi()
        }
    }

    if (showCamera) {
        CameraDialog(
            onCaptured = { uri ->
                imageUri = uri
                showCamera = false
            },
            onDismiss = { showCamera = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Report Damage") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Reporting damage for '${record.itemName}'", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray).clickable { showCamera = true }, contentAlignment = Alignment.Center) {
                if (imageUri != null) {
                    AsyncImage(imageUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Text("Capture Damage Photo", color = Color.Gray)
                    }
                }
            }

            Text("Item Condition", fontWeight = FontWeight.Bold)
            val conditions = listOf("Minor", "Moderate", "Severe")
            Column {
                conditions.forEach { cond ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { condition = cond }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = condition == cond, onClick = { condition = cond })
                        Text(cond)
                    }
                }
            }

            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Detailed Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(value = chargeAmount, onValueChange = { chargeAmount = it }, label = { Text("Repair/Compensation Charge") }, modifier = Modifier.fillMaxWidth(), prefix = { Text("₩ ") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))

            Button(
                onClick = {
                    if (chargeAmount.isBlank()) {
                        vm.setUiError("Please specify a charge amount")
                        return@Button
                    }
                    vm.submitDamageReport(record, condition, description, chargeAmount.toLongOrNull() ?: 0L, imageUri)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = uiState !is UiState.Loading
            ) {
                if (uiState is UiState.Loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Submit Damage Report")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DamageReportDetailScreen(record: BorrowRecord, role: String, lenderVm: LenderViewModel, borrowerVm: BorrowerViewModel, onBack: () -> Unit) {
    val report = record.damageReport ?: return
    val uiState = if (role == "lender") lenderVm.ui.collectAsState().value else borrowerVm.ui.collectAsState().value
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) {
            if (role == "lender") lenderVm.resetUi() else borrowerVm.resetUi()
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Damage Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            AsyncImage(report.damageImageUrl, null, modifier = Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Crop)
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(record.itemName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                StatusChip(report.status.uppercase(), OverdueRed)
                
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow("Condition", report.condition)
                        DetailRow("Reported On", fmtDate(report.reportedAt))
                        DetailRow("Requested Charge", krw(report.chargeAmount), warn = true)
                    }
                }
                
                Text("Description", fontWeight = FontWeight.Bold)
                Text(report.description.ifEmpty { "No additional details provided." })

                Spacer(Modifier.height(24.dp))

                if (role == "borrower" && record.status == "damaged") {
                    Button(onClick = { borrowerVm.payDamageCharge(record, report.chargeAmount) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Pay ₩${report.chargeAmount} & Close")
                    }
                    OutlinedButton(onClick = { borrowerVm.requestNegotiation(record) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Request Negotiation")
                    }
                } else if (role == "lender" && record.status == "negotiating") {
                    Button(onClick = { lenderVm.completeNegotiation(record) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Mark Negotiation as Resolved")
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(0.3f), RoundedCornerShape(8.dp)).padding(16.dp)) {
                        Text(
                            if (record.status == "negotiating") "Negotiation in progress. Borrower has been notified."
                            else "Damage report submitted. Waiting for borrower response.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DamageHistoryScreen(history: List<DamageHistory>, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Damage History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { pad ->
        if (history.isEmpty()) {
            EmptyState("No Damage History", "Records of resolved damages will appear here.", Icons.Default.History)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history) { item ->
                    DamageHistoryCard(item)
                }
            }
        }
    }
}

@Composable
fun DamageHistoryCard(history: DamageHistory) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(history.damageImageUrl, null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(history.itemName, fontWeight = FontWeight.Bold)
                Text(history.condition, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(fmtDate(history.timestamp), style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(krw(history.chargeAmount), fontWeight = FontWeight.Bold, color = OverdueRed)
                StatusChip(history.paymentStatus, if (history.paymentStatus == "Paid") AvailGreen else LentOrange)
            }
        }
    }
}
