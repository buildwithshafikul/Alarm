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

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted! You can now record your voice.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Microphone permission is required to record your voice.", Toast.LENGTH_LONG).show()
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
                        },
                        onRequestRecordPermission = {
                            requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
    onRequestNotificationPermission: () -> Unit,
    onRequestRecordPermission: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Loaded settings
    var selectedSound by remember { mutableStateOf("chime.mp3") }
    var restartOnBoot by remember { mutableStateOf(true) }
    var isSalamEnabled by remember { mutableStateOf(false) }
    var nextTriggerTime by remember { mutableStateOf(0L) }

    // Custom voice recorder state
    val audioRecorder = remember { AudioRecorder() }
    var isRecording by remember { mutableStateOf(false) }
    val recordFile = remember { java.io.File(context.filesDir, "custom_voice.mp4") }
    var hasRecording by remember { mutableStateOf(recordFile.exists()) }

    // Dropdown list state
    var soundDropdownExpanded by remember { mutableStateOf(false) }
    val availableSounds = remember(hasRecording) {
        val baseList = mutableListOf(
            "bell.mp3" to "Digital Bell",
            "chime.mp3" to "Calm Chime",
            "beep.mp3" to "Breeze Alarm",
            "zen.mp3" to "Zen Temple Chime"
        )
        if (hasRecording) {
            baseList.add(0, "custom_voice.mp4" to "My Voice Recording (আমার কণ্ঠ)")
        }
        baseList
    }

    // Sync state with SharedPreferences
    LaunchedEffect(Unit) {
        val loaded = SettingsRepository.loadSettings(context)
        selectedSound = loaded.selectedSound
        restartOnBoot = loaded.restartOnBoot
        isSalamEnabled = loaded.isSalamEnabled
        nextTriggerTime = SettingsRepository.getNextTriggerTime(context)
        
        // Setup initial channels
        NotificationHelper.createNotificationChannels(context)

        // Ensure active alarms and background service state are running
        if (loaded.isSalamEnabled) {
            AlarmScheduler.scheduleNextHourlyAlarm(context)
        }
        AlarmScheduler.updateServiceState(context)
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
            if (selectedSound == "custom_voice.mp4") {
                val file = java.io.File(context.filesDir, "custom_voice.mp4")
                if (file.exists()) {
                    player.setDataSource(file.absolutePath)
                } else {
                    Toast.makeText(context, "No custom voice recorded yet!", Toast.LENGTH_SHORT).show()
                    isTestingSound = false
                    return
                }
            } else {
                val afd = context.assets.openFd("audio/$selectedSound")
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
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
    val nextTriggerStr = remember(nextTriggerTime, isSalamEnabled) {
        if (!isSalamEnabled || nextTriggerTime == 0L) {
            "ঘণ্টাভিত্তিক সালাম ও সময় ঘোষণা বন্ধ আছে"
        } else {
            val sdf = SimpleDateFormat("hh:00 a", Locale.getDefault())
            "পরবর্তী ঘোষণা: " + sdf.format(Date(nextTriggerTime))
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
                    text = "সালাম ও সময় ঘোষণা",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.testTag("app_title")
                )
                Text(
                    text = "প্রতি ঘণ্টায় চমৎকার এলার্ম টোন এবং সালাম সহ সময় ঘোষণা",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.8f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timer Status Panel (Styled as Elegant Dark Active/Inactive hero)
        val statusBg by animateColorAsState(
            targetValue = if (isSalamEnabled) MaterialTheme.colorScheme.primaryContainer else cardBackground,
            label = "status_color"
        )
        val statusTextIconColor = if (isSalamEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = statusBg),
            border = if (!isSalamEnabled) BorderStroke(1.dp, cardBorderColor) else null
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
                        imageVector = if (isSalamEnabled) Icons.Default.AlarmOn else Icons.Default.AlarmOff,
                        contentDescription = "Alarm status",
                        tint = statusTextIconColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1.0f)) {
                    Text(
                        text = if (isSalamEnabled) "সার্ভিস সক্রিয় রয়েছে (Running)" else "সার্ভিস বন্ধ রয়েছে (Stopped)",
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

        // Master Switch Card for Service Enabling/Disabling
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(0.8f)) {
                    Text(
                        text = "সার্ভিস সচল করুন (Enable Service)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "সক্রিয় রাখলে অ্যাপটি অলটাইম ব্যাকগ্রাউন্ডে সচল থাকবে এবং প্রতি এক ঘণ্টা পর পর সালাম ও সময় ঘোষণা করবে।",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                Switch(
                    checked = isSalamEnabled,
                    onCheckedChange = { newValue ->
                        isSalamEnabled = newValue
                        val currentSettings = SettingsRepository.loadSettings(context).copy(isSalamEnabled = newValue)
                        SettingsRepository.saveSettings(context, currentSettings)
                        if (newValue) {
                            AlarmScheduler.scheduleNextHourlyAlarm(context)
                        } else {
                            AlarmScheduler.cancelHourlyAlarm(context)
                        }
                        AlarmScheduler.updateServiceState(context)
                        nextTriggerTime = SettingsRepository.getNextTriggerTime(context)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = Color(0xFFCAC4D0),
                        uncheckedTrackColor = Color(0xFF49454F)
                    ),
                    modifier = Modifier.testTag("salam_announcement_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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
                    text = "এলার্ম টোন নির্বাচন করুন",
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
                                        text = "বর্তমান টোন",
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
                                    val currentSettings = SettingsRepository.loadSettings(context).copy(selectedSound = file)
                                    SettingsRepository.saveSettings(context, currentSettings)
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
                    Text(text = if (isTestingSound) "টোন পরীক্ষা বন্ধ করুন" else "টোন পরীক্ষা করুন")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Custom Voice Recorder Card
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
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Recorder Icon",
                        tint = colorPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "নিজের কণ্ঠে রেকর্ড করুন (Voice Recorder)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1.0f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "আপনি চাইলে নিজের কণ্ঠে যেকোনো রিমাইন্ডার রেকর্ড করে সেটিকে এলার্ম টোন হিসেবে ব্যবহার করতে পারেন।",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Display current status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Recording Status Badge
                    val statusText = if (isRecording) "রেকর্ডিং হচ্ছে..." else if (hasRecording) "রেকর্ড পাওয়া গেছে" else "কোনো রেকর্ড নেই"
                    val statusColor = if (isRecording) Color(0xFFE57373) else if (hasRecording) Color(0xFF81C784) else Color(0xFFCAC4D0)
                    val statusBg = if (isRecording) Color(0xFF3C090A) else if (hasRecording) Color(0xFF0C2411) else Color(0xFF1C1B1F)

                    Surface(
                        color = statusBg,
                        border = BorderStroke(1.dp, statusColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = statusColor),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // If recording exists, show delete action
                    if (hasRecording && !isRecording) {
                        IconButton(
                            onClick = {
                                if (recordFile.exists()) {
                                    recordFile.delete()
                                }
                                hasRecording = false
                                if (selectedSound == "custom_voice.mp4") {
                                    selectedSound = "chime.mp3"
                                    val currentSettings = SettingsRepository.loadSettings(context).copy(selectedSound = "chime.mp3")
                                    SettingsRepository.saveSettings(context, currentSettings)
                                }
                                Toast.makeText(context, "রেকর্ড ফাইলটি মুছে ফেলা হয়েছে।", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete recording",
                                tint = Color(0xFFE57373)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Record button
                    val hasMicPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    Button(
                        onClick = {
                            if (!hasMicPermission) {
                                onRequestRecordPermission()
                            } else {
                                if (isRecording) {
                                    // Stop recording
                                    audioRecorder.stopRecording()
                                    isRecording = false
                                    hasRecording = recordFile.exists()
                                    // Auto-select recording
                                    if (hasRecording) {
                                        selectedSound = "custom_voice.mp4"
                                        val currentSettings = SettingsRepository.loadSettings(context).copy(selectedSound = "custom_voice.mp4")
                                        SettingsRepository.saveSettings(context, currentSettings)
                                        Toast.makeText(context, "কণ্ঠ রেকর্ড সফল হয়েছে এবং এলার্ম টোন হিসেবে সেট হয়েছে!", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    // Start recording
                                    if (recordFile.exists()) {
                                        recordFile.delete()
                                    }
                                    audioRecorder.startRecording(context, recordFile)
                                    isRecording = true
                                    hasRecording = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFE57373) else MaterialTheme.colorScheme.secondary,
                            contentColor = if (isRecording) Color(0xFF3C090A) else MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.StopCircle else Icons.Default.Mic,
                            contentDescription = "Mic operation"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRecording) "রেকর্ডিং বন্ধ করুন" else "কণ্ঠ রেকর্ড করুন")
                    }

                    // Play button (only if recording exists and not currently recording)
                    if (hasRecording && !isRecording) {
                        var isPlayingCustomVoice by remember { mutableStateOf(false) }
                        var customVoicePlayer: MediaPlayer? by remember { mutableStateOf(null) }

                        DisposableEffect(Unit) {
                            onDispose {
                                customVoicePlayer?.release()
                            }
                        }

                        Button(
                            onClick = {
                                if (isPlayingCustomVoice) {
                                    try {
                                        customVoicePlayer?.stop()
                                        customVoicePlayer?.release()
                                        customVoicePlayer = null
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Error stopping custom preview", e)
                                    }
                                    isPlayingCustomVoice = false
                                } else {
                                    val player = MediaPlayer()
                                    customVoicePlayer = player
                                    try {
                                        player.setDataSource(recordFile.absolutePath)
                                        player.prepare()
                                        player.start()
                                        isPlayingCustomVoice = true
                                        player.setOnCompletionListener {
                                            player.release()
                                            customVoicePlayer = null
                                            isPlayingCustomVoice = false
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Error playing custom preview", e)
                                        Toast.makeText(context, "রেকর্ড ফাইলটি বাজানো যায়নি।", Toast.LENGTH_SHORT).show()
                                        isPlayingCustomVoice = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingCustomVoice) Color(0xFF81C784) else Color(0xFF49454F),
                                contentColor = if (isPlayingCustomVoice) Color(0xFF0C2411) else Color(0xFFD0BCFF)
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlayingCustomVoice) Icons.Default.StopCircle else Icons.Default.PlayArrow,
                                contentDescription = "Play operation"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPlayingCustomVoice) "বন্ধ করুন" else "শুনুন")
                        }
                    }
                }
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
