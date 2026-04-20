package com.lendlink.ui.borrower

import android.Manifest
import android.net.Uri
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.lendlink.data.model.*
import com.lendlink.ui.common.*
import com.lendlink.ui.lender.*
import com.lendlink.ui.theme.*
import com.lendlink.viewmodel.*
import java.util.concurrent.Executors

// ══════════════════════════════════════════════════════════════
//  BORROWER DASHBOARD
// ══════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowerDashboard(
    vm: BorrowerViewModel,
    borrowerName: String,
    onItemClick: (BorrowRecord) -> Unit,
    onScan: () -> Unit,
    onNotifications: () -> Unit,
    onPaymentHistory: () -> Unit,
    onBorrowHistory: () -> Unit,
    onProfile: () -> Unit,
    onBrowseItem: (Item) -> Unit,
    onLogout: () -> Unit
) {
    val wallet by vm.wallet.collectAsState()
    val searchQueryAvailable by vm.searchQueryAvailable.collectAsState()
    val searchQueryActive by vm.searchQueryActive.collectAsState()
    val notifications by vm.notifications.collectAsState()
    val unreadCount = notifications.count { !it.read }
    val ui by vm.ui.collectAsState()
    val scanned by vm.scanned.collectAsState()
    val filteredAvailable by vm.filteredAllItems.collectAsState()
    val filteredActive by vm.filteredActive.collectAsState()
    val systemCats by vm.systemCategories.collectAsState()
    val activeCats by vm.activeCategories.collectAsState()
    val selectedCatAvailable by vm.selectedCatAvailable.collectAsState()
    val selectedCatActive by vm.selectedCatActive.collectAsState()

    val snack = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(ui) {
        val s = ui
        if (s is UiState.Success) { snack.showSnackbar(s.message); vm.resetUi() }
        else if (s is UiState.Error) { snack.showSnackbar(s.message); vm.resetUi() }
    }

    if (scanned != null) {
        BorrowConfirmDialog(
            item = scanned!!,
            onDismiss = {
                vm.clearScanned()
                vm.resetUi()
            },
            onConfirm = {
                vm.confirmBorrow(it)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(borrowerName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Borrower", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    }
                },
                actions = {
                    IconButton(onClick = onNotifications) {
                        BadgedBox(badge = { if (unreadCount > 0) Badge { Text(unreadCount.toString()) } }) {
                            Icon(Icons.Default.Notifications, null)
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("My Profile") }, leadingIcon = { Icon(Icons.Default.Person, null) }, onClick = { menu = false; onProfile() })
                            DropdownMenuItem(text = { Text("Payment History") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) }, onClick = { menu = false; onPaymentHistory() })
                            DropdownMenuItem(text = { Text("Borrow History") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { menu = false; onBorrowHistory() })
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Logout", color = MaterialTheme.colorScheme.error) }, 
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error) }, 
                                onClick = { 
                                    menu = false
                                    onLogout() 
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, actionIconContentColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Default.QrCodeScanner, null) },
                text = { Text("Scan to Borrow") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            // Wallet Card 
            WalletCard(wallet, borrowerName, "Wallet Balance")

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = MaterialTheme.colorScheme.primary)
                },
                divider = { HorizontalDivider() }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Active Borrows", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Available to Borrow", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search 
            OutlinedTextField(
                value = if (selectedTab == 0) searchQueryActive else searchQueryAvailable,
                onValueChange = { if (selectedTab == 0) vm.updateSearchQueryActive(it) else vm.updateSearchQueryAvailable(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(if (selectedTab == 0) "Search active borrows..." else "Search items...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp)
            )

            // Categories
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val selected = if (selectedTab == 0) selectedCatActive == "All" else selectedCatAvailable == "All"
                    FilterChip(selected = selected, onClick = { if (selectedTab == 0) vm.selectCatActive("All") else vm.selectCatAvailable("All") }, label = { Text("All") })
                }
                val categoriesToShow = if (selectedTab == 0) activeCats else systemCats
                items(categoriesToShow) { cat ->
                    val selected = if (selectedTab == 0) selectedCatActive == cat else selectedCatAvailable == cat
                    FilterChip(selected = selected, onClick = { if (selectedTab == 0) vm.selectCatActive(cat) else vm.selectCatAvailable(cat) }, label = { Text(cat) })
                }
            }

            if (selectedTab == 0) {
                // Active Borrows Tab
                if (filteredActive.isEmpty()) {
                    EmptyState("No active borrows", "Try changing your search or category", Icons.Default.SearchOff)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(filteredActive) { record -> ActiveBorrowCard(record, onItemClick) }
                    }
                }
            } else {
                // Available to Borrow Tab
                if (filteredAvailable.isEmpty()) {
                    EmptyState("No items found", "Try changing your search or category", Icons.Default.SearchOff)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(filteredAvailable) { item -> ItemCard(item, onBrowseItem) }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveBorrowCard(record: BorrowRecord, onClick: (BorrowRecord) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick(record) }, shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemImage(record.itemImageUrl, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.itemName, fontWeight = FontWeight.Bold)
                Text("Lender: ${record.lenderName}", style = MaterialTheme.typography.bodySmall)
            }
            StatusChip("Active", Color(0xFF2196F3))
        }
    }
}

@Composable
fun ItemCard(item: Item, onClick: (Item) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick(item) }, shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemImage(item.imageUrl, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(item.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(krw(item.price), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("Lender: ${item.lenderName}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowerAvailableItemDetailScreen(item: Item, onBack: () -> Unit, onScan: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(item.name) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).verticalScroll(rememberScrollState())) {
            ItemImage(item.imageUrl, modifier = Modifier.fillMaxWidth().height(240.dp), clip = false) 
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); StatusChip("Available", Color(0xFF4CAF50)) }
                Text(krw(item.price), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                DetailRow("Category", item.category)
                DetailRow("Lender Name", item.lenderName)
                DetailRow("Lender Phone", item.lenderPhone.ifEmpty { "Not provided" })
                DetailRow("Lender Location", item.lenderLocation)
                DetailRow("Description", item.description.ifEmpty { "No description provided." })
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(12.dp)); Text("To borrow this item, please meet the lender and scan their item QR code.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                        Button(onClick = onScan, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan QR Code Now") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowerItemDetailScreen(record: BorrowRecord, vm: BorrowerViewModel, onBack: () -> Unit) {
    var showReturnDialog by remember { mutableStateOf(false) }
    val uiState by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        val s = uiState
        if (s is UiState.Success) {
            snack.showSnackbar(s.message)
            vm.resetUi()
        }
    }

    if (showReturnDialog) {
        AlertDialog(
            onDismissRequest = { showReturnDialog = false },
            title = { Text("Request Return?") },
            text = { Text("Do you want to request a return for ${record.itemName}? The lender must accept this request to finalize the return.") },
            confirmButton = { Button({ showReturnDialog = false; vm.requestReturn(record) }) { Text("Request") } },
            dismissButton = { TextButton({ showReturnDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(title = { Text("Borrowed Item") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White))
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).verticalScroll(rememberScrollState())) {
            ItemImage(record.itemImageUrl, modifier = Modifier.fillMaxWidth().height(300.dp), clip = false)
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.itemName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StatusChip("Active", Color(0xFF2196F3))
                }
                Text(krw(record.price), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                
                HorizontalDivider()
                DetailRow("Category", record.itemCategory.ifBlank { "General" })
                DetailRow("Lender Name", record.lenderName)
                DetailRow("Lender Phone", record.lenderPhone.ifEmpty { "Not provided" })
                DetailRow("Lender Location", record.lenderLocation)
                DetailRow("Borrowed At", fmtDate(record.borrowedAt))
                DetailRow("Deadline", fmtDate(record.deadline))
                
                Spacer(Modifier.height(8.dp))
                
                val isRequested = record.status == "return_requested"
                
                Button(
                    onClick = { if (!isRequested) showReturnDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isRequested,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE0E0E0),
                        disabledContentColor = Color(0xFF757575)
                    )
                ) {
                    Icon(
                        if (isRequested) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.AssignmentReturn, 
                        null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isRequested) "Return Request Sent" else "Request Return to Lender", 
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (isRequested) {
                    Text(
                        "Please wait for the lender to accept your return request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun BorrowConfirmDialog(item: Item, onDismiss: () -> Unit, onConfirm: (Item) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Borrow ${item.name}?") },
        text = { Text("Are you sure you want to borrow this item for ${krw(item.price)}? The amount will be deducted from your wallet.") },
        confirmButton = { Button({ onConfirm(item) }) { Text("Confirm Borrow") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(user: User, authVm: AuthViewModel, onBack: () -> Unit) {
    var showCamera by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showCamera) {
        CameraDialog(
            onCaptured = { uri ->
                authVm.updateProfileImage(user.uid, uri)
                showCamera = false
            },
            onDismiss = { showCamera = false }
        )
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("My Profile") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                AsyncImage(model = user.profileImageUrl.ifEmpty { "https://ui-avatars.com/api/?name=${user.username}&background=random" }, contentDescription = null, modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.LightGray), contentScale = ContentScale.Crop)
                IconButton(onClick = { showCamera = true }, modifier = Modifier.align(Alignment.BottomEnd).size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) { Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp), tint = Color.White) }
            }
            Spacer(Modifier.height(16.dp))
            Text(user.username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(user.role.replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(32.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProfileItem(Icons.Default.Email, "Email", user.email)
                    ProfileItem(Icons.Default.Phone, "Phone", user.phone)
                    ProfileItem(Icons.Default.LocationOn, "Location", user.locationAddress)
                }
            }
        }
    }
}

@Composable
fun ProfileItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ScanQRScreen(vm: BorrowerViewModel, onScanned: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var isScanning by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().padding(pad)) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also {
                                it.setAnalyzer(cameraExecutor) { imageProxy ->
                                    if (isScanning) {
                                        val buffer = imageProxy.planes[0].buffer
                                        val data = ByteArray(buffer.remaining())
                                        buffer.get(data)
                                        val source = PlanarYUVLuminanceSource(
                                            data,
                                            imageProxy.width,
                                            imageProxy.height,
                                            0,
                                            0,
                                            imageProxy.width,
                                            imageProxy.height,
                                            false
                                        )
                                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                                        try {
                                            val result = MultiFormatReader().apply {
                                                setHints(
                                                    mapOf(
                                                        DecodeHintType.POSSIBLE_FORMATS to listOf(
                                                            BarcodeFormat.QR_CODE
                                                        )
                                                    )
                                                )
                                            }.decode(bitmap)
                                            val code = result.text
                                            if (!code.isNullOrEmpty()) {
                                                isScanning = false
                                                vm.fetchItem(code)
                                                ContextCompat.getMainExecutor(context).execute {
                                                    onScanned()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    }
                                    imageProxy.close()
                                }
                            }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // QR Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sizePx = 250.dp.toPx()
                val left = (size.width - sizePx) / 2
                val top = (size.height - sizePx) / 2
                drawRect(Color.Black.copy(alpha = 0.5f))
                drawRect(
                    color = Color.Transparent,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                )
                drawRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
