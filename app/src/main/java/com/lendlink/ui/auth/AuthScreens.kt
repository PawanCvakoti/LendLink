package com.lendlink.ui.auth

import android.Manifest
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.lendlink.viewmodel.AuthState
import com.lendlink.viewmodel.AuthViewModel

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
            is AuthState.Error -> { snack.showSnackbar(s.msg); vm.reset() }
            else -> {}
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
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
    var lat by remember { mutableStateOf(0.0) }
    var lng by remember { mutableStateOf(0.0) }
    var showMap by remember { mutableStateOf(false) }

    val locPerm = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

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
                    value = locationAddress.ifEmpty { "Tap to select your location" },
                    onValueChange = {},
                    label = { Text("Location") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = { Icon(Icons.Default.Map, null) },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                // Overlay to ensure click works on all devices (especially Samsung)
                Box(modifier = Modifier.matchParentSize().clickable {
                    showMap = true
                    if (!locPerm.status.isGranted) locPerm.launchPermissionRequest()
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
    onConfirm: (lat: Double, lng: Double, address: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLat by remember { mutableStateOf(35.9078) } // Center of South Korea
    var selectedLng by remember { mutableStateOf(127.7669) }
    var displayAddress by remember { mutableStateOf("Tap on map to select") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp),
        title = {
            Column {
                Text("Pick Meeting Point", fontWeight = FontWeight.Bold)
                Text("Tap map in South Korea to select", style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))) {
                AndroidView(factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            setGeolocationEnabled(true)
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()

                        addJavascriptInterface(WebAppInterface { lat, lng ->
                            selectedLat = lat
                            selectedLng = lng
                            displayAddress = "Lat: ${String.format("%.4f", lat)}, Lng: ${String.format("%.4f", lng)}"
                        }, "AndroidBridge")

                        // Improved HTML with restricted bounds to South Korea
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
                                    // South Korea Bounds
                                    var southWest = L.latLng(33.0, 124.0),
                                        northEast = L.latLng(39.0, 131.0),
                                        bounds = L.latLngBounds(southWest, northEast);

                                    var map = L.map('map', {
                                        zoomControl: true,
                                        attributionControl: false,
                                        maxBounds: bounds,
                                        maxBoundsViscosity: 1.0
                                    }).setView([35.9078, 127.7669], 7);
                                    
                                    // Use a different CDN for tiles to avoid "Access Blocked"
                                    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                        minZoom: 6,
                                        maxZoom: 18
                                    }).addTo(map);

                                    var marker;
                                    map.on('click', function(e) {
                                        if (marker) map.removeLayer(marker);
                                        marker = L.marker(e.latlng).addTo(map);
                                        AndroidBridge.onMapClick(e.latlng.lat, e.latlng.lng);
                                    });
                                    
                                    setTimeout(function() {
                                        map.invalidateSize();
                                    }, 500);
                                </script>
                            </body>
                            </html>
                        """.trimIndent()
                        // Loading with a proper data URI to bypass certain WebView origin restrictions
                        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }
                }, modifier = Modifier.fillMaxSize())
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedLat, selectedLng, displayAddress) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) { Text("Confirm This Location") }
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
