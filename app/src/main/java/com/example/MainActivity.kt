package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
            Toast.makeText(this, "Notification permission is granted!", Toast.LENGTH_SHORT).show()
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
    var isSalamEnabled by remember { mutableStateOf(false) }
    var nextTriggerTime by remember { mutableStateOf(0L) }

    // Sync state with SharedPreferences
    LaunchedEffect(Unit) {
        val loaded = SettingsRepository.loadSettings(context)
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

    // TTS announcer for testing
    var isTestingSound by remember { mutableStateOf(false) }
    var ttsAnnouncer by remember { mutableStateOf<BengaliTTSAnnouncer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            ttsAnnouncer?.release()
        }
    }

    fun playTestingSound() {
        if (isTestingSound) {
            ttsAnnouncer?.release()
            ttsAnnouncer = null
            isTestingSound = false
            return
        }
        
        isTestingSound = true
        val announcer = BengaliTTSAnnouncer(context)
        ttsAnnouncer = announcer
        
        // Play current time Bengali sentence preceded by Salam
        val sentence = BengaliTTSAnnouncer.getBengaliTimeSpeech()
        announcer.speak(sentence)
        
        // Automatically set testing sound to false after 6 seconds to reset button indicator state
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isTestingSound = false
        }, 6000)
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

    // Theme colors (Material 3 elegant dark styling)
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
        // Hero Header Banner using our beautiful new generated App Icon as background banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_app_icon_1782908322398),
                contentDescription = "Beautiful App Icon Emblem",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Gradient Overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            // Title over banner
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Text(
                    text = "সালাম ও সময় ঘোষণা",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.testTag("app_title")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "প্রতি ঘণ্টায় সালাম ও সময় ঘোষণা করার একটি অফলাইন ও নির্ভরযোগ্য সার্ভিস",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.8f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Ask for Notification permission on Android 13+ if not granted
        val isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!isNotificationGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "নোটিফিকেশন পারমিশন প্রয়োজন",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "অ্যাপটি ব্যাকগ্রাউন্ডে সচল রয়েছে বোঝাতে নোটিফিকেশন পারমিশন দেওয়া আবশ্যক। দয়া করে নিচের বাটনে ক্লিক করে পারমিশন দিন।",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestNotificationPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("পারমিশন দিন", color = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Timer Status Panel
        val statusBg by animateColorAsState(
            targetValue = if (isSalamEnabled) MaterialTheme.colorScheme.primaryContainer else cardBackground,
            label = "status_color"
        )
        val statusTextIconColor = if (isSalamEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
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
                        text = if (isSalamEnabled) "সার্ভিসটি সক্রিয় আছে" else "সার্ভিসটি বন্ধ আছে",
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

        Spacer(modifier = Modifier.height(16.dp))

        // Master Switch Card for Service Enabling/Disabling
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(0.8f)) {
                    Text(
                        text = "সার্ভিস অন / অফ করুন",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "অন করা থাকলে অ্যাপটি অলটাইম ব্যাকগ্রাউন্ডে সচল থাকবে এবং প্রতি ঘণ্টার শুরুতে সালাম ও সময় জানাবে।",
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

        // Sound Test Section (Beautiful elegant button)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "সালাম ও সময় ঘোষণা পরীক্ষা করুন",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "নিচের বাটনে চাপ দিয়ে এখনকার সময় বাংলা উচ্চারণে এবং সালাম সহ কেমন শোনাবে তা পরীক্ষা করে দেখুন।",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = { playTestingSound() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("test_sound_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTestingSound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isTestingSound) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isTestingSound) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Test voice output"
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isTestingSound) "ঘোষণা বন্ধ করুন" else "সালাম ও সময় শুনুন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Hello $name! Welcome to Hourly Salam & Time Announcement.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
    }
}
