package com.lendlink.ui.auth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.location.Geocoder
import android.location.LocationManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.lendlink.viewmodel.AuthState
import com.lendlink.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

// ── LOGIN SCREEN ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onSuccess: (role: String) -> Unit,
    onRegister: () -> Unit
) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVis by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthState.Success -> { onSuccess(s.user.role); vm.reset() }
            is AuthState.Error -> { 
                snack.showSnackbar(s.msg)
                vm.reset() 
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { 
            SnackbarHost(snack) { data ->
                val isAccountNotFound = data.visuals.message.contains("Account not found", ignoreCase = true)
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isAccountNotFound) Color(0xFFD32F2F) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isAccountNotFound) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } 
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(80.dp))
            Text("LendLink", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary)
            Text("Peer Lending Platform", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(.6f))
            Spacer(Modifier.height(48.dp))
            Text("Welcome back", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Sign in to your account", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(.6f))
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(value = email, onValueChange = { email = it },
                label = { Text("Email address") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = pass, onValueChange = { pass = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = if (passVis) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ passVis = !passVis }) {
                    Icon(if (passVis) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(28.dp))

            if (state is AuthState.Loading) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { vm.login(email.trim(), pass) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = email.isNotBlank() && pass.isNotBlank()) {
                    Text("Sign In", fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Don't have an account?")
                TextButton(onClick = onRegister) { Text("Register") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── REGISTER SCREEN ───────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RegisterScreen(
    vm: AuthViewModel,
    onRegistered: () -> Unit,
    onLogin: () -> Unit
) {
    val state by vm.state.collectAsState()
    val usernameTaken by vm.usernameTaken.collectAsState()
    val emailTaken by vm.emailTaken.collectAsState()
    val phoneTaken by vm.phoneTaken.collectAsState()

    val snack = remember { SnackbarHostState() }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("borrower") }
    var passVis by remember { mutableStateOf(false) }
    var confirmPassVis by remember { mutableStateOf(false) }
    var locationAddress by remember { mutableStateOf("") }
    var lat by remember { mutableDoubleStateOf(35.9078) } // Center of South Korea
    var lng by remember { mutableDoubleStateOf(127.7669) }
    var showMap by remember { mutableStateOf(false) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locPerm = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val location = fusedLocationClient.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(5000)
                    .build(),
                null
            ).await()

            location?.let {
                lat = it.latitude
                lng = it.longitude
                
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    locationAddress = addresses[0].getAddressLine(0)
                } else {
                    locationAddress = "Lat: ${String.format("%.4f", it.latitude)}, Lng: ${String.format("%.4f", it.longitude)}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fetchAndShowMap() {
        scope.launch {
            isFetchingLocation = true
            fetchCurrentLocation()
            isFetchingLocation = false
            showMap = true
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fetchAndShowMap()
        }
    }

    fun checkAndRequestLocation() {
        if (!locPerm.status.isGranted) {
            locPerm.launchPermissionRequest()
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        
        if (!isGpsEnabled) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
            val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
            val client = LocationServices.getSettingsClient(context)
            val task = client.checkLocationSettings(builder.build())

            task.addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                        locationLauncher.launch(intentSenderRequest)
                    } catch (_: IntentSender.SendIntentException) {}
                }
            }
            task.addOnSuccessListener {
                fetchAndShowMap()
            }
        } else {
            fetchAndShowMap()
        }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthState.Success -> {
                // Instantly navigate on success for a snappier feel
                onRegistered()
                vm.reset()
            }
            is AuthState.Error -> { snack.showSnackbar(s.msg); vm.reset() }
            else -> {}
        }
    }

    if (showMap) {
        LocationPickerDialog(
            initialLat = lat,
            initialLng = lng,
            onConfirm = { selectedLat, selectedLng, address ->
                lat = selectedLat; lng = selectedLng; locationAddress = address
                showMap = false
            },
            onDismiss = { showMap = false }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(title = { Text("Create Account") },
                navigationIcon = { IconButton(onClick = onLogin) { Icon(Icons.Default.ArrowBack, null) } })
        }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Join LendLink", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Create your account to get started", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(.6f))
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    vm.validateUsername(it)
                },
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                isError = usernameTaken,
                supportingText = { if (usernameTaken) Text("This username is already taken", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    vm.validateEmail(it)
                },
                label = { Text("Email address") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                isError = emailTaken || (email.isNotEmpty() && !email.endsWith("@gmail.com")),
                supportingText = {
                    if (emailTaken) {
                        Text("This email is already registered", color = MaterialTheme.colorScheme.error)
                    } else if (email.isNotEmpty() && !email.endsWith("@gmail.com")) {
                        Text("Enter a valid email address (e.g., name@gmail.com)", color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    vm.validatePhone(it)
                },
                label = { Text("Phone number") },
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                isError = phoneTaken,
                supportingText = { if (phoneTaken) Text("This phone number is already used", color = MaterialTheme.colorScheme.error) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))

            // Registration validation logic
            val isEmailValid = email.endsWith("@gmail.com")
            val passwordPattern = remember { Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{6,}\$") }
            val isRegPassValid = pass.isEmpty() || passwordPattern.matches(pass)
            val isPassValid = passwordPattern.matches(pass)

            OutlinedTextField(value = pass, onValueChange = { pass = it },
                label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = if (passVis) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ passVis = !passVis }) {
                    Icon(if (passVis) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                isError = !isRegPassValid,
                supportingText = { if (!isRegPassValid) Text("Requires 6+ chars, uppercase, lowercase, and a digit", color = MaterialTheme.colorScheme.error) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = confirmPass, onValueChange = { confirmPass = it },
                label = { Text("Confirm password") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = if (confirmPassVis) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ confirmPassVis = !confirmPassVis }) {
                    Icon(if (confirmPassVis) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                isError = confirmPass.isNotEmpty() && pass != confirmPass,
                supportingText = { if (confirmPass.isNotEmpty() && pass != confirmPass) Text("Passwords do not match") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(14.dp))

            // Location picker
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = if (isFetchingLocation) "Fetching location..." else locationAddress.ifEmpty { "Tap to select your location" },
                    onValueChange = {},
                    label = { Text("Location") },
                    leadingIcon = { 
                        if (isFetchingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailingIcon = { Icon(Icons.Default.Map, null) },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = if (isFetchingLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                // Overlay to ensure click works on all devices (especially Samsung)
                Box(modifier = Modifier.matchParentSize().clickable(enabled = !isFetchingLocation) {
                    checkAndRequestLocation()
                })
            }
            Spacer(Modifier.height(16.dp))

            // Role selection
            Text("Register as", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (role == "lender") {
                    Button(onClick = { role = "lender" }, modifier = Modifier.weight(1f).height(44.dp)) { Text("Lender") }
                } else {
                    OutlinedButton(onClick = { role = "lender" }, modifier = Modifier.weight(1f).height(44.dp)) { Text("Lender") }
                }
                if (role == "borrower") {
                    Button(onClick = { role = "borrower" }, modifier = Modifier.weight(1f).height(44.dp)) { Text("Borrower") }
                } else {
                    OutlinedButton(onClick = { role = "borrower" }, modifier = Modifier.weight(1f).height(44.dp)) { Text("Borrower") }
                }
            }
            if (role == "borrower") {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text("Borrower accounts start with ₩100,000 in credits",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(24.dp))

            val valid = username.isNotBlank() && isEmailValid && phone.isNotBlank() &&
                    isPassValid && pass == confirmPass &&
                    !usernameTaken && !emailTaken && !phoneTaken

            if (state is AuthState.Loading) {
                CircularProgressIndicator()
            } else {
                Button(onClick = {
                    vm.register(username.trim(), email.trim(), phone.trim(), pass, role, lat, lng, locationAddress)
                }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = valid) {
                    Text("Create Account", fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?")
                TextButton(onClick = onLogin) { Text("Sign In") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── LOCATION PICKER DIALOG (OpenStreetMap + Leaflet) ────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerDialog(
    initialLat: Double,
    initialLng: Double,
    onConfirm: (lat: Double, lng: Double, address: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLat by remember(initialLat) { mutableDoubleStateOf(initialLat) }
    var selectedLng by remember(initialLng) { mutableDoubleStateOf(initialLng) }
    var displayAddress by remember { mutableStateOf("Fetching address...") }
    val context = LocalContext.current

    // Update display address when coordinates change
    LaunchedEffect(selectedLat, selectedLng) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(selectedLat, selectedLng, 1)
            if (!addresses.isNullOrEmpty()) {
                displayAddress = addresses[0].getAddressLine(0)
            } else {
                displayAddress = "Lat: ${String.format("%.4f", selectedLat)}, Lng: ${String.format("%.4f", selectedLng)}"
            }
        } catch (_: Exception) {
            displayAddress = "Lat: ${String.format("%.4f", selectedLat)}, Lng: ${String.format("%.4f", selectedLng)}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp),
        title = {
            Column {
                Text("Select Location", fontWeight = FontWeight.Bold)
                Text(displayAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 2)
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))) {
                AndroidView(factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                        }
                        
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // If we have initial coordinates, set the marker and view
                                view?.loadUrl("javascript:updateMap($initialLat, $initialLng)")
                            }
                        }

                        addJavascriptInterface(WebAppInterface { lat, lng ->
                            selectedLat = lat
                            selectedLng = lng
                        }, "AndroidBridge")

                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                                <style>
                                    body { margin: 0; padding: 0; background-color: #eee; }
                                    #map { height: 100vh; width: 100vw; background: white; }
                                </style>
                            </head>
                            <body>
                                <div id="map"></div>
                                <script>
                                    var map = L.map('map', {
                                        zoomControl: true,
                                        attributionControl: false
                                    }).setView([$initialLat, $initialLng], 15);
                                    
                                    // Use CartoDB Positron tiles which are more reliable in WebViews
                                    L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
                                        attribution: '&copy; OpenStreetMap &copy; CartoDB',
                                        subdomains: 'abcd',
                                        maxZoom: 20
                                    }).addTo(map);

                                    var marker = L.marker([$initialLat, $initialLng]).addTo(map);

                                    function updateMap(lat, lng) {
                                        var latlng = L.latLng(lat, lng);
                                        if (marker) map.removeLayer(marker);
                                        marker = L.marker(latlng).addTo(map);
                                        map.setView(latlng, 18); // Zoom in closer for exact location
                                    }

                                    map.on('click', function(e) {
                                        updateMap(e.latlng.lat, e.latlng.lng);
                                        AndroidBridge.onMapClick(e.latlng.lat, e.latlng.lng);
                                    });
                                    
                                    setTimeout(function() {
                                        map.invalidateSize();
                                    }, 500);
                                </script>
                            </body>
                            </html>
                        """.trimIndent()
                        loadDataWithBaseURL("https://carto.com", html, "text/html", "UTF-8", null)
                    }
                }, modifier = Modifier.fillMaxSize())
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedLat, selectedLng, displayAddress) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) { Text("Confirm Location") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// JavaScript Bridge for Location Picker
class WebAppInterface(private val onLocationReceived: (Double, Double) -> Unit) {
    @JavascriptInterface
    fun onMapClick(lat: Double, lng: Double) {
        onLocationReceived(lat, lng)
    }
}
