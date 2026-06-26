package com.aris.voice.triggers.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aris.voice.BaseNavigationActivity
import com.aris.voice.R
import com.aris.voice.triggers.Trigger
import com.aris.voice.triggers.TriggerManager
import com.aris.voice.triggers.TriggerMonitoringService
import com.aris.voice.triggers.TriggerType

class TriggersActivity : BaseNavigationActivity() {

    private lateinit var triggerManager: TriggerManager
    private lateinit var sharedPreferences: SharedPreferences

    private fun getMockGeocode(address: String): Pair<Double, Double>? {
        val query = address.lowercase().trim()
        val locations = mapOf(
            "home" to Pair(28.6139, 77.2090),
            "office" to Pair(28.5355, 77.3910),
            "work" to Pair(28.5355, 77.3910),
            "gym" to Pair(28.6250, 77.3730),
            "cafe" to Pair(28.6304, 77.2177),
            "starbucks" to Pair(28.6304, 77.2177),
            "delhi" to Pair(28.6139, 77.2090),
            "new delhi" to Pair(28.6139, 77.2090),
            "mumbai" to Pair(19.0760, 72.8777),
            "bangalore" to Pair(12.9716, 77.5946),
            "bengaluru" to Pair(12.9716, 77.5946),
            "noida" to Pair(28.5700, 77.3200),
            "gurgaon" to Pair(28.4595, 77.0266),
            "gurugram" to Pair(28.4595, 77.0266),
            "airport" to Pair(28.5562, 77.1000),
            "delhi airport" to Pair(28.5562, 77.1000),
            "taj mahal" to Pair(27.1751, 78.0421),
            "agra" to Pair(27.1751, 78.0421),
            "hyderabad" to Pair(17.3850, 78.4867),
            "chennai" to Pair(13.0827, 80.2707),
            "pune" to Pair(18.5204, 73.8567),
            "kolkata" to Pair(22.5726, 88.3639)
        )
        
        if (locations.containsKey(query)) {
            return locations[query]
        }
        
        for ((key, value) in locations) {
            if (query.contains(key) || key.contains(query)) {
                return value
            }
        }
        return null
    }

    private suspend fun geocodeAddress(context: Context, address: String): android.location.Address? {
        return withContext(Dispatchers.IO) {
            try {
                if (android.location.Geocoder.isPresent()) {
                    val geocoder = android.location.Geocoder(context)
                    val results = geocoder.getFromLocationName(address, 1)
                    if (!results.isNullOrEmpty()) {
                        results[0]
                    } else null
                } else null
            } catch (e: Exception) {
                android.util.Log.e("Geocoder", "Geocoding failed for $address", e)
                null
            }
        }
    }

    private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (android.location.Geocoder.isPresent()) {
                    val geocoder = android.location.Geocoder(context)
                    val results = geocoder.getFromLocation(lat, lng, 1)
                    if (!results.isNullOrEmpty()) {
                        val addr = results[0]
                        val parts = mutableListOf<String>()
                        for (i in 0..addr.maxAddressLineIndex) {
                            parts.add(addr.getAddressLine(i))
                        }
                        parts.joinToString(", ")
                    } else null
                } else null
            } catch (e: Exception) {
                android.util.Log.e("Geocoder", "Reverse geocoding failed", e)
                null
            }
        }
    }

    companion object {
        const val PREFS_NAME = "TriggerPrefs"
        const val KEY_TRIGGERS_ENABLED = "triggers_enabled"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_triggers)

        triggerManager = TriggerManager.getInstance(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val composeView = findViewById<ComposeView>(R.id.compose_view)
        composeView.setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF2196F3),
                    secondary = Color(0xFF03A9F4),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                AutomationDashboardScreen()
            }
        }
    }

    override fun getContentLayoutId(): Int = R.layout.activity_triggers
    override fun getCurrentNavItem(): BaseNavigationActivity.NavItem = BaseNavigationActivity.NavItem.TRIGGERS

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AutomationDashboardScreen() {
        val context = LocalContext.current
        var triggersEnabled by remember {
            mutableStateOf(sharedPreferences.getBoolean(KEY_TRIGGERS_ENABLED, false))
        }
        var triggersList by remember {
            mutableStateOf(triggerManager.getTriggers())
        }
        var showCreatorCard by remember { mutableStateOf(false) }

        var locationPermissionGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            )
        }

        val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                          permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            locationPermissionGranted = granted
            if (granted) {
                Toast.makeText(context, "Location permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        fun refreshTriggers() {
            triggersList = triggerManager.getTriggers()
        }

        fun toggleService(enabled: Boolean) {
            triggersEnabled = enabled
            sharedPreferences.edit().putBoolean(KEY_TRIGGERS_ENABLED, enabled).apply()
            val serviceIntent = Intent(context, TriggerMonitoringService::class.java)
            try {
                if (enabled) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Toast.makeText(context, "A.R.I.S Automation Service Started", Toast.LENGTH_SHORT).show()
                } else {
                    context.stopService(serviceIntent)
                    Toast.makeText(context, "A.R.I.S Automation Service Stopped", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("TriggersActivity", "Failed to start/stop service", e)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "A.R.I.S Automations",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onBackPressed() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E1E1E)
                    )
                )
            },
            floatingActionButton = {
                if (!showCreatorCard) {
                    ExtendedFloatingActionButton(
                        onClick = { showCreatorCard = true },
                        containerColor = Color(0xFF2196F3),
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                        text = { Text("Add Automation") }
                    )
                }
            },
            containerColor = Color(0xFF121212)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (triggersEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (triggersEnabled) "Service Active" else "Service Inactive",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Run tasks based on geofence & time triggers.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = triggersEnabled,
                            onCheckedChange = { toggleService(it) }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showCreatorCard,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    TriggerCreatorPanel(
                        onDismiss = { showCreatorCard = false },
                        onSave = { newTrigger ->
                            triggerManager.addTrigger(newTrigger)
                            refreshTriggers()
                            showCreatorCard = false
                            Toast.makeText(context, "Automation Saved Successfully!", Toast.LENGTH_SHORT).show()
                        },
                        locationPermissionGranted = locationPermissionGranted,
                        onRequestLocationPermission = {
                            locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Active Routines",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (triggersList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "No Automations",
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No routines configured yet.", color = Color.Gray)
                            Text("Create one using the button below or by voice!", color = Color.DarkGray, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(triggersList) { trigger ->
                            TriggerRowItem(
                                trigger = trigger,
                                onToggle = { isEnabled ->
                                    val updated = trigger.copy(isEnabled = isEnabled)
                                    triggerManager.updateTrigger(updated)
                                    refreshTriggers()
                                },
                                onDelete = {
                                    triggerManager.removeTrigger(trigger)
                                    refreshTriggers()
                                    Toast.makeText(context, "Automation Deleted", Toast.LENGTH_SHORT).show()
                                },
                                onTest = {
                                    triggerManager.executeInstruction(trigger.instruction)
                                    Toast.makeText(context, "Trigger Testing Triggered!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun TriggerRowItem(
        trigger: Trigger,
        onToggle: (Boolean) -> Unit,
        onDelete: () -> Unit,
        onTest: () -> Unit
    ) {
        val icon = when (trigger.type) {
            TriggerType.SCHEDULED_TIME -> Icons.Default.DateRange
            TriggerType.LOCATION_BASED -> Icons.Default.Place
            TriggerType.CHARGING_STATE -> Icons.Default.Home
            TriggerType.BATTERY_LEVEL -> Icons.Default.Star
            else -> Icons.Default.PlayArrow
        }

        val details = when (trigger.type) {
            TriggerType.SCHEDULED_TIME -> {
                val h = trigger.hour ?: 12
                val m = trigger.minute ?: 0
                val amPm = if (h >= 12) "PM" else "AM"
                val hFormatted = if (h > 12) h - 12 else if (h == 0) 12 else h
                val mFormatted = String.format("%02d", m)
                "Scheduled for: $hFormatted:$mFormatted $amPm"
            }
            TriggerType.LOCATION_BASED -> {
                val radius = trigger.locationRadiusMeters?.toInt() ?: 200
                if (!trigger.label.isNullOrBlank()) {
                    "📌 Near: ${trigger.label} (within ${radius}m)"
                } else {
                    "📌 Geofenced at: (Lat: ${trigger.locationLatitude}, Lng: ${trigger.locationLongitude}) within ${radius}m"
                }
            }
            else -> "Trigger: ${trigger.type.name}"
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF2B2B2B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        trigger.instruction,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        details,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTest) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Test Trigger", tint = Color(0xFF4CAF50))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE91E63))
                    }
                    Switch(
                        checked = trigger.isEnabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TriggerCreatorPanel(
        onDismiss: () -> Unit,
        onSave: (Trigger) -> Unit,
        locationPermissionGranted: Boolean,
        onRequestLocationPermission: () -> Unit
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        
        var selectedType by remember { mutableStateOf(TriggerType.SCHEDULED_TIME) }
        
        var hourInput by remember { mutableStateOf(12) }
        var minuteInput by remember { mutableStateOf(0) }

        var latInput by remember { mutableStateOf("") }
        var lngInput by remember { mutableStateOf("") }
        var radiusSlider by remember { mutableStateOf(200f) }

        var placeSearchQuery by remember { mutableStateOf("") }
        var resolvedAddressName by remember { mutableStateOf("") }
        var customPlaceName by remember { mutableStateOf("") }
        var isSearchingPlace by remember { mutableStateOf(false) }
        var showAdvancedCoords by remember { mutableStateOf(false) }

        var isPredefined by remember { mutableStateOf(true) }
        var customActionText by remember { mutableStateOf("") }
        var selectedPredefinedAction by remember { mutableStateOf("Play music on YouTube") }

        val predefinedActions = listOf(
            "Play music on YouTube",
            "Play dynamic tracks on Spotify",
            "Open Google Maps and Navigate",
            "Take a quick snap with Camera",
            "Set alarm for the next morning",
            "Check for nearby charging stations",
            "Open System Settings"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252525)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("New Automation Rule", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selectedType == TriggerType.SCHEDULED_TIME) Color(0xFF2196F3) else Color.Transparent)
                            .clickable { selectedType = TriggerType.SCHEDULED_TIME }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Time Scheduled", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selectedType == TriggerType.LOCATION_BASED) Color(0xFF2196F3) else Color.Transparent)
                            .clickable { selectedType = TriggerType.LOCATION_BASED }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Location Geofence", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedType == TriggerType.SCHEDULED_TIME) {
                    Column {
                        Text("Select Trigger Time:", color = Color.LightGray, fontSize = 14.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Hour: $hourInput", color = Color.White, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = hourInput.toFloat(),
                                    onValueChange = { hourInput = it.toInt() },
                                    valueRange = 0f..23f,
                                    steps = 23,
                                    modifier = Modifier.width(130.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Minute: $minuteInput", color = Color.White, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = minuteInput.toFloat(),
                                    onValueChange = { minuteInput = it.toInt() },
                                    valueRange = 0f..59f,
                                    steps = 59,
                                    modifier = Modifier.width(130.dp)
                                )
                            }
                        }
                    }
                } else {
                    Column {
                        Text("1. Search Place or Address Name", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = placeSearchQuery,
                                onValueChange = { placeSearchQuery = it },
                                placeholder = { Text("e.g. Noida Sector 62, Delhi Airport, Home...", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                trailingIcon = {
                                    if (placeSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { placeSearchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Button(
                                onClick = {
                                    if (placeSearchQuery.isBlank()) {
                                        Toast.makeText(context, "Please enter a search query!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isSearchingPlace = true
                                    coroutineScope.launch {
                                        val mockCoords = getMockGeocode(placeSearchQuery)
                                        if (mockCoords != null) {
                                            latInput = mockCoords.first.toString()
                                            lngInput = mockCoords.second.toString()
                                            resolvedAddressName = placeSearchQuery.trim()
                                            customPlaceName = placeSearchQuery.trim()
                                            Toast.makeText(context, "Matched Preset Location!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val addr = geocodeAddress(context, placeSearchQuery)
                                            if (addr != null) {
                                                latInput = addr.latitude.toString()
                                                lngInput = addr.longitude.toString()
                                                val cleanName = addr.featureName ?: addr.locality ?: placeSearchQuery.trim()
                                                resolvedAddressName = cleanName
                                                customPlaceName = cleanName
                                                Toast.makeText(context, "Found: $cleanName", Toast.LENGTH_SHORT).show()
                                            } else {
                                                try {
                                                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                                                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                                                    if (loc != null) {
                                                        val hash = placeSearchQuery.hashCode().toDouble() / 10000000.0
                                                        val finalLat = loc.latitude + (hash % 0.01)
                                                        val finalLng = loc.longitude + ((hash * 1.3) % 0.01)
                                                        latInput = finalLat.toString()
                                                        lngInput = finalLng.toString()
                                                        resolvedAddressName = placeSearchQuery.trim()
                                                        customPlaceName = placeSearchQuery.trim()
                                                        Toast.makeText(context, "Set virtual coordinates near you", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        latInput = "28.6139"
                                                        lngInput = "77.2090"
                                                        resolvedAddressName = placeSearchQuery.trim()
                                                        customPlaceName = placeSearchQuery.trim()
                                                        Toast.makeText(context, "Set mock coordinates", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: SecurityException) {
                                                    latInput = "28.6139"
                                                    lngInput = "77.2090"
                                                    resolvedAddressName = placeSearchQuery.trim()
                                                    customPlaceName = placeSearchQuery.trim()
                                                }
                                            }
                                        }
                                        isSearchingPlace = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (isSearchingPlace) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Find", fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Quick Places Presets (Tapping auto-fills)", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))

                        val presets = listOf(
                            Triple("🏠 Home", 28.6139, 77.2090),
                            Triple("💼 Work", 28.5355, 77.3910),
                            Triple("🏋️ Gym", 28.6250, 77.3730),
                            Triple("☕ Cafe", 28.6304, 77.2177),
                            Triple("✈️ Airport", 28.5562, 77.1000)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF333333))
                                        .clickable {
                                            latInput = preset.second.toString()
                                            lngInput = preset.third.toString()
                                            placeSearchQuery = preset.first.substring(2)
                                            resolvedAddressName = preset.first.substring(2)
                                            customPlaceName = preset.first.substring(2)
                                            Toast.makeText(context, "Loaded preset: ${preset.first}", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(preset.first, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Current Location Helper", color = Color.LightGray, fontSize = 12.sp)
                            Button(
                                onClick = {
                                    if (locationPermissionGranted) {
                                        try {
                                            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                            val providers = lm.getProviders(true)
                                            var lastKnown: Location? = null
                                            for (provider in providers) {
                                                val loc = lm.getLastKnownLocation(provider) ?: continue
                                                if (lastKnown == null || loc.accuracy < lastKnown.accuracy) {
                                                    lastKnown = loc
                                                }
                                            }
                                            if (lastKnown != null) {
                                                latInput = lastKnown.latitude.toString()
                                                lngInput = lastKnown.longitude.toString()
                                                
                                                coroutineScope.launch {
                                                    val rName = reverseGeocode(context, lastKnown.latitude, lastKnown.longitude)
                                                    if (rName != null) {
                                                        resolvedAddressName = rName
                                                        customPlaceName = "Current Location"
                                                        placeSearchQuery = rName
                                                    } else {
                                                        resolvedAddressName = "Current Location"
                                                        customPlaceName = "Current Location"
                                                    }
                                                }
                                                Toast.makeText(context, "Current Location picked!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Requesting live location update...", Toast.LENGTH_SHORT).show()
                                                val listener = object : android.location.LocationListener {
                                                    override fun onLocationChanged(location: Location) {
                                                        latInput = location.latitude.toString()
                                                        lngInput = location.longitude.toString()
                                                        coroutineScope.launch {
                                                            val rName = reverseGeocode(context, location.latitude, location.longitude)
                                                            resolvedAddressName = rName ?: "Current Location"
                                                            customPlaceName = "Current Location"
                                                            placeSearchQuery = rName ?: "Current Location"
                                                        }
                                                        Toast.makeText(context, "Live Location set!", Toast.LENGTH_SHORT).show()
                                                        lm.removeUpdates(this)
                                                    }
                                                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                                                    override fun onProviderEnabled(provider: String) {}
                                                    override fun onProviderDisabled(provider: String) {}
                                                }
                                                try {
                                                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                                                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, android.os.Looper.getMainLooper())
                                                    } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                                                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, android.os.Looper.getMainLooper())
                                                    } else {
                                                        Toast.makeText(context, "Please turn on device location services!", Toast.LENGTH_LONG).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Failed to request live location: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: SecurityException) {
                                            Toast.makeText(context, "Permissions missing", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        onRequestLocationPermission()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pick Current Location", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        if (resolvedAddressName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Selected Place: $resolvedAddressName",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("2. Custom Name for Place (e.g. My Gym, Noida Office)", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customPlaceName,
                            onValueChange = { customPlaceName = it },
                            placeholder = { Text("Name this location...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2196F3),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val radiusDesc = when {
                            radiusSlider <= 150f -> "🎯 Exact Place (100m - 150m)"
                            radiusSlider <= 300f -> "📍 Building Block (200m - 300m)"
                            radiusSlider <= 600f -> "🏠 Neighborhood (400m - 600m)"
                            else -> "🌍 Whole Region (800m - 1000m)"
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("3. Geofence Radius: ${radiusSlider.toInt()}m", color = Color.LightGray, fontSize = 13.sp)
                            Text(radiusDesc, color = Color(0xFF2196F3), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = radiusSlider,
                            onValueChange = { radiusSlider = it },
                            valueRange = 100f..1000f,
                            steps = 8
                        )

                        Row(
                            modifier = Modifier
                                .clickable { showAdvancedCoords = !showAdvancedCoords }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (showAdvancedCoords) "Hide Advanced Coordinates ⚙️" else "Show Advanced Coordinates ⚙️",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        if (showAdvancedCoords) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = latInput,
                                    onValueChange = { latInput = it },
                                    label = { Text("Latitude", fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                OutlinedTextField(
                                    value = lngInput,
                                    onValueChange = { lngInput = it },
                                    label = { Text("Longitude", fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Predefined Tasks vs Custom Task:", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isPredefined) Color(0xFF2196F3) else Color.Transparent)
                            .clickable { isPredefined = true }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Predefined Intent", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (!isPredefined) Color(0xFF2196F3) else Color.Transparent)
                            .clickable { isPredefined = false }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Custom Text / Task", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isPredefined) {
                    var expandedDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { expandedDropdown = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedPredefinedAction, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2D2D2D))
                        ) {
                            predefinedActions.forEach { actionName ->
                                DropdownMenuItem(
                                    text = { Text(actionName, color = Color.White) },
                                    onClick = {
                                        selectedPredefinedAction = actionName
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customActionText,
                        onValueChange = { customActionText = it },
                        label = { Text("Enter custom typed task...") },
                        placeholder = { Text("e.g., Mute volume and read unread notifications") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2196F3),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val taskInstruction = if (isPredefined) selectedPredefinedAction else customActionText
                        if (taskInstruction.isBlank()) {
                            Toast.makeText(context, "Please specify a task action!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (selectedType == TriggerType.LOCATION_BASED) {
                            val lat = latInput.toDoubleOrNull()
                            val lng = lngInput.toDoubleOrNull()
                            if (lat == null || lng == null) {
                                Toast.makeText(context, "Please search/enter a valid place or coordinates!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val finalLabel = if (customPlaceName.isNotBlank()) {
                                customPlaceName.trim()
                            } else if (resolvedAddressName.isNotBlank()) {
                                resolvedAddressName.trim()
                            } else {
                                "Location ($lat, $lng)"
                            }

                            val trigger = Trigger(
                                type = TriggerType.LOCATION_BASED,
                                instruction = taskInstruction,
                                locationLatitude = lat,
                                locationLongitude = lng,
                                locationRadiusMeters = radiusSlider,
                                label = finalLabel
                            )
                            onSave(trigger)
                        } else {
                            val trigger = Trigger(
                                type = TriggerType.SCHEDULED_TIME,
                                instruction = taskInstruction,
                                hour = hourInput,
                                minute = minuteInput,
                                label = "Time: $taskInstruction"
                            )
                            onSave(trigger)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("Save Rule", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
