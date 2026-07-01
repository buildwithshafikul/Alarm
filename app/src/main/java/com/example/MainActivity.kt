package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notifications enabled successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission is required for alerts.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Loaded settings
    var intervalInput by remember { mutableStateOf("15") }
    var selectedUnit by remember { mutableStateOf("Minutes") } // "Minutes" or "Hours"
    var isEnabled by remember { mutableStateOf(false) }
    var selectedSound by remember { mutableStateOf("chime.mp3") }
    var restartOnBoot by remember { mutableStateOf(true) }
    var nextTriggerTime by remember { mutableStateOf(0L) }

    // Dropdown list state
    var soundDropdownExpanded by remember { mutableStateOf(false) }
    val availableSounds = listOf(
        "bell.mp3" to "Digital Bell",
        "chime.mp3" to "Calm Chime",
        "beep.mp3" to "Breeze Alarm",
        "zen.mp3" to "Zen Temple Chime"
    )

    // Sync state with SharedPreferences
    LaunchedEffect(Unit) {
        val loaded = SettingsRepository.loadSettings(context)
        intervalInput = loaded.intervalValue.toString()
        selectedUnit = loaded.intervalUnit
        isEnabled = loaded.isEnabled
        selectedSound = loaded.selectedSound
        restartOnBoot = loaded.restartOnBoot
        nextTriggerTime = SettingsRepository.getNextTriggerTime(context)
        
        // Setup initial channels
        NotificationHelper.createNotificationChannels(context)
    }

    // Sound player helper for testing
    var mediaPlayer: MediaPlayer? by remember { mutableStateOf(null) }
    var isTestingSound by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    fun stopTestingSound() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed stopping sound", e)
        }
        isTestingSound = false
    }

    fun playTestingSound() {
        if (isTestingSound) {
            stopTestingSound()
            return
        }
        
        isTestingSound = true
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            val afd = context.assets.openFd("audio/$selectedSound")
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.isLooping = false
            player.prepare()
            player.start()
            player.setOnCompletionListener {
                stopTestingSound()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed playing custom asset, using default system", e)
            try {
                player.release()
                mediaPlayer = null
                
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, defaultUri)
                ringtone?.play()
                
                // Automatically stop indicator after 3 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    ringtone?.stop()
                    isTestingSound = false
                }, 3000)
            } catch (ex: Exception) {
                Log.e("MainActivity", "Failed system tone play", ex)
                isTestingSound = false
            }
        }
    }

    // Format current next trigger time
    val nextTriggerStr = remember(nextTriggerTime, isEnabled) {
        if (!isEnabled || nextTriggerTime == 0L) {
            "No Active Timer Scheduled"
        } else {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            "Next Trigger: " + sdf.format(Date(nextTriggerTime))
        }
    }

    // Theme values (Material 3 elegant dark styling)
    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorBackground = MaterialTheme.colorScheme.background
    val cardBackground = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorBackground)
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp)
    ) {
        // Hero Header Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_reminder_hero_1782890981940),
                contentDescription = "Glowing digital alarm clock banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
            // Title over banner
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Hourly Reminder",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.testTag("app_title")
                )
                Text(
                    text = "Never miss a beat in your busy day",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.8f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timer Status Panel (Styled as Elegant Dark Active/Inactive hero)
        val statusBg by animateColorAsState(
            targetValue = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else cardBackground,
            label = "status_color"
        )
        val statusTextIconColor = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = statusBg),
            border = if (!isEnabled) BorderStroke(1.dp, cardBorderColor) else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(statusTextIconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.AlarmOn else Icons.Default.AlarmOff,
                        contentDescription = "Alarm status",
                        tint = statusTextIconColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1.0f)) {
                    Text(
                        text = if (isEnabled) "Active Monitoring" else "Reminder Timer Stopped",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = statusTextIconColor
                        ),
                        modifier = Modifier.testTag("reminder_status")
                    )
                    Text(
                        text = nextTriggerStr,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = statusTextIconColor.copy(alpha = 0.8f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Timer Config Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Configure Reminder",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Interval Input
                    OutlinedTextField(
                        value = intervalInput,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                intervalInput = newValue
                            }
                        },
                        label = { Text("Interval") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1.0f)
                            .testTag("interval_input"),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = "Schedule")
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Unit selector (Minutes / Hours)
                    Column(modifier = Modifier.weight(1.0f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                    .background(if (selectedUnit == "Minutes") MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { selectedUnit = "Minutes" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "MIN",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedUnit == "Minutes") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                                    .background(if (selectedUnit == "Hours") MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { selectedUnit = "Hours" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "HRS",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedUnit == "Hours") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Preset values
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val presets = listOf(
                    (5 to "Minutes"), (10 to "Minutes"), (15 to "Minutes"),
                    (30 to "Minutes"), (45 to "Minutes"), (1 to "Hours"),
                    (2 to "Hours"), (3 to "Hours")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val firstFour = presets.take(4)
                    firstFour.forEach { (value, unit) ->
                        val isSelected = intervalInput == value.toString() && selectedUnit == unit
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                intervalInput = value.toString()
                                selectedUnit = unit
                            },
                            label = { Text("$value m") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4A4458),
                                selectedLabelColor = Color(0xFFD0BCFF),
                                containerColor = Color.Transparent,
                                labelColor = Color(0xFFE6E1E5)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                selectedBorderColor = Color(0xFFD0BCFF),
                                selectedBorderWidth = 1.dp,
                                borderColor = Color(0xFF49454F),
                                borderWidth = 1.dp,
                                enabled = true,
                                selected = isSelected
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val lastFour = presets.drop(4)
                    lastFour.forEach { (value, unit) ->
                        val isSelected = intervalInput == value.toString() && selectedUnit == unit
                        val label = if (unit == "Hours") "$value hr" else "$value m"
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                intervalInput = value.toString()
                                selectedUnit = unit
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4A4458),
                                selectedLabelColor = Color(0xFFD0BCFF),
                                containerColor = Color.Transparent,
                                labelColor = Color(0xFFE6E1E5)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                selectedBorderColor = Color(0xFFD0BCFF),
                                selectedBorderWidth = 1.dp,
                                borderColor = Color(0xFF49454F),
                                borderWidth = 1.dp,
                                enabled = true,
                                selected = isSelected
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sound Selection & Playback Testing Card (Polished Card design for maximum compatibility)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Notification Sound Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Sound select selector button mimicking TextField
                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        onClick = { soundDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sound_selector_card"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Music icon",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Alert Tone",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = availableSounds.find { it.first == selectedSound }?.second ?: selectedSound,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown indicator",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = soundDropdownExpanded,
                        onDismissRequest = { soundDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        availableSounds.forEach { (file, labelName) ->
                            DropdownMenuItem(
                                text = { Text(labelName) },
                                onClick = {
                                    selectedSound = file
                                    soundDropdownExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (selectedSound == file) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Selection State",
                                        tint = if (selectedSound == file) colorPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test sound player buttons
                Button(
                    onClick = { playTestingSound() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("test_sound_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTestingSound) MaterialTheme.colorScheme.primary else Color(0xFF49454F),
                        contentColor = if (isTestingSound) MaterialTheme.colorScheme.onPrimary else Color(0xFFD0BCFF)
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isTestingSound) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Audio feedback"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isTestingSound) "Stop Sound Test" else "Test Selected Sound Tone")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large Control Panel Buttons: Start and Stop Reminders
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // STOP Button
            Button(
                onClick = {
                    if (isEnabled) {
                        isEnabled = false
                        nextTriggerTime = 0L
                        
                        // Save State
                        val currentSettings = AlarmSettings(
                            intervalValue = intervalInput.toIntOrNull() ?: 15,
                            intervalUnit = selectedUnit,
                            isEnabled = false,
                            selectedSound = selectedSound,
                            restartOnBoot = restartOnBoot
                        )
                        SettingsRepository.saveSettings(context, currentSettings)
                        
                        // Stop Background Timers
                        AlarmScheduler.cancelAlarm(context)
                        ForegroundService.stopService(context)
                        
                        Toast.makeText(context, "Hourly Reminder alerts stopped.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1.0f)
                    .height(56.dp)
                    .testTag("stop_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF49454F),
                    contentColor = Color(0xFFD0BCFF),
                    disabledContainerColor = Color(0xFF49454F).copy(alpha = 0.4f),
                    disabledContentColor = Color(0xFFD0BCFF).copy(alpha = 0.4f)
                ),
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop alarm")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stop Alerts")
            }

            // START Button
            Button(
                onClick = {
                    val parsedVal = intervalInput.toIntOrNull()
                    if (parsedVal == null || parsedVal <= 0) {
                        Toast.makeText(context, "Please enter a valid alarm interval.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Check notification permission if Tiramisu+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasNotificationPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasNotificationPerm) {
                            onRequestNotificationPermission()
                            return@Button
                        }
                    }

                    // Check schedule exact alarm permissions
                    if (!AlarmScheduler.hasExactAlarmPermission(context)) {
                        Toast.makeText(context, "Please grant Schedule Exact Alarm permission in settings for exact timings.", Toast.LENGTH_LONG).show()
                    }

                    isEnabled = true

                    // Save State
                    val currentSettings = AlarmSettings(
                        intervalValue = parsedVal,
                        intervalUnit = selectedUnit,
                        isEnabled = true,
                        selectedSound = selectedSound,
                        restartOnBoot = restartOnBoot
                    )
                    SettingsRepository.saveSettings(context, currentSettings)

                    // Start scheduler & service
                    AlarmScheduler.scheduleNextAlarm(context)
                    ForegroundService.startService(context)

                    // Update UI display
                    nextTriggerTime = SettingsRepository.getNextTriggerTime(context)

                    Toast.makeText(context, "Periodic Reminder started successfully!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1.0f)
                    .height(56.dp)
                    .testTag("start_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start alarm")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Alerts")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Settings Section Header (Collapsible Settings Card)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings icon",
                        tint = colorPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Advanced Device Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1.0f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle 1: Restart automatically after reboot
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(0.85f)) {
                        Text(
                            text = "Auto-Restart on Boot",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Automatically reschedule active alarm reminders when your phone restarts.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                    Switch(
                        checked = restartOnBoot,
                        onCheckedChange = { newValue ->
                            restartOnBoot = newValue
                            val currentSettings = SettingsRepository.loadSettings(context).copy(restartOnBoot = newValue)
                            SettingsRepository.saveSettings(context, currentSettings)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF381E72),
                            checkedTrackColor = Color(0xFFD0BCFF),
                            uncheckedThumbColor = Color(0xFFCAC4D0),
                            uncheckedTrackColor = Color(0xFF49454F)
                        ),
                        modifier = Modifier.testTag("boot_restart_switch")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Section 2: Permissions Audit Dashboard
                Text(
                    text = "System Permissions Diagnosis",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Audit exact alarm permission status
                val isExactAlarmGranted = AlarmScheduler.hasExactAlarmPermission(context)
                PermissionDiagnosisRow(
                    permissionName = "Schedule Exact Alarms",
                    explanation = "Allows alarm triggers to execute on the precise second instead of being batched by Android to conserve battery.",
                    isGranted = isExactAlarmGranted,
                    onFixClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            }
                        } else {
                            Toast.makeText(context, "Exact alarms are granted automatically on this Android version.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Audit Notification permission status
                val isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                PermissionDiagnosisRow(
                    permissionName = "Notification Alerts",
                    explanation = "Ensures periodic alert banners display successfully in the status bar and on the lockscreen.",
                    isGranted = isNotificationGranted,
                    onFixClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            onRequestNotificationPermission()
                        } else {
                            Toast.makeText(context, "Notifications are granted automatically on this Android version.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Audit battery optimization status
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                } else {
                    true
                }
                PermissionDiagnosisRow(
                    permissionName = "Battery Saver Exemption",
                    explanation = "Prevents Android from putting the app's background service to sleep or killing exact alarms in deep doze.",
                    isGranted = isIgnoringBatteryOptimizations,
                    onFixClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                @SuppressLint("BatteryLife")
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        } else {
                            Toast.makeText(context, "Battery saver exemptions do not apply on this Android version.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionDiagnosisRow(
    permissionName: String,
    explanation: String,
    isGranted: Boolean,
    onFixClick: () -> Unit
) {
    val statusColor = if (isGranted) Color(0xFF81C784) else Color(0xFFE57373)
    val containerBg = MaterialTheme.colorScheme.background

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .border(
                width = 1.dp,
                color = statusColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.0f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Status icon",
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = permissionName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            )
        }
        if (!isGranted) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onFixClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE57373),
                    contentColor = Color(0xFF3C090A)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Fix", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Deprecated-styled package-level Greeting composable to ensure local screenshot tests compile!
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Hello $name! Welcome to Hourly Reminder Alarm Test.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
    }
}
