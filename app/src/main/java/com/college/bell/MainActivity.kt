package com.college.bell

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.college.bell.ui.*

/**
 * The whole app is one Activity with five tabs. No navigation library: a single
 * `screen` variable and a `when` is less to learn and less to break.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opening the app is also a cheap chance to make sure an alarm is armed.
        Scheduler.armNext(this)
        setContent { BellApp() }
    }
}

enum class Screen(val label: String, val icon: ImageVector, val needsPin: Boolean) {
    DASHBOARD("Today", Icons.Filled.Home, false),
    TIMETABLES("Timetables", Icons.Filled.Schedule, true),
    ASSIGN("Calendar", Icons.Filled.DateRange, true),
    HISTORY("History", Icons.Filled.History, false),
    SETTINGS("Settings", Icons.Filled.Settings, true),
}

@Composable
fun BellApp() {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(Store.load(context)) }

    // Android 13+ hides the "Ringing — STOP" notification unless the user grants this.
    // The bell still sounds without it, but staff lose the visible indicator and the
    // stop control in the notification shade, so ask once on first launch.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* declined is survivable — bells are unaffected */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var screen by remember { mutableStateOf(Screen.DASHBOARD) }
    var unlocked by remember { mutableStateOf(false) }

    // Every change in the app goes through here: save to disk, re-arm the alarm,
    // redraw. One path in and out means the schedule can never drift from the screen.
    val update: (Config) -> Unit = { next ->
        Store.save(context, next)
        cfg = next
    }

    MaterialTheme(colorScheme = lightColorScheme()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { s ->
                        NavigationBarItem(
                            selected = screen == s,
                            onClick = { screen = s },
                            icon = { Icon(s.icon, contentDescription = s.label) },
                            label = { Text(s.label) },
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val locked = cfg.pin != null && screen.needsPin && !unlocked
                when {
                    locked -> PinGate(cfg.pin!!) { unlocked = true }
                    screen == Screen.DASHBOARD -> DashboardScreen(cfg, update)
                    screen == Screen.TIMETABLES -> TimetablesScreen(cfg, update)
                    screen == Screen.ASSIGN -> AssignScreen(cfg, update)
                    screen == Screen.HISTORY -> HistoryScreen(cfg, update)
                    screen == Screen.SETTINGS -> SettingsScreen(cfg, update)
                }
            }
        }
    }
}
