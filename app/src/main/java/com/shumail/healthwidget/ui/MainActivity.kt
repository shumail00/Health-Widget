package com.shumail.healthwidget.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shumail.healthwidget.data.MedicationRepository
import com.shumail.healthwidget.ui.screens.CalendarScreen
import com.shumail.healthwidget.ui.screens.MedicationsScreen
import com.shumail.healthwidget.ui.screens.ProgressScreen
import com.shumail.healthwidget.ui.screens.SettingsScreen
import com.shumail.healthwidget.ui.screens.TodayScreen
import com.shumail.healthwidget.ui.theme.HealthWidgetTheme

enum class ScreenDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY("Today", Icons.Filled.Today, Icons.Outlined.Today),
    CALENDAR("Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    MEDICATIONS("Meds", Icons.Filled.Medication, Icons.Outlined.Medication),
    PROGRESS("Progress", Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repository = remember { MedicationRepository.getInstance(applicationContext) }
            var themeMode by remember { mutableStateOf(repository.getThemeMode()) }

            val isDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            HealthWidgetTheme(darkTheme = isDarkTheme) {
                MainAppScaffold(
                    repository = repository,
                    currentThemeMode = themeMode,
                    onThemeModeChanged = { themeMode = it }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    repository: MedicationRepository,
    currentThemeMode: String,
    onThemeModeChanged: (String) -> Unit
) {
    var selectedScreen by remember { mutableStateOf(ScreenDestination.TODAY) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Synchronize timers & state when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                repository.reconcileActiveTimer()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                ScreenDestination.values().forEach { destination ->
                    val isSelected = selectedScreen == destination
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedScreen = destination },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) },
                        modifier = Modifier.testTag("nav_item_${destination.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedScreen) {
                ScreenDestination.TODAY -> TodayScreen(repository = repository)
                ScreenDestination.CALENDAR -> CalendarScreen(repository = repository)
                ScreenDestination.MEDICATIONS -> MedicationsScreen(repository = repository)
                ScreenDestination.PROGRESS -> ProgressScreen(repository = repository)
                ScreenDestination.SETTINGS -> SettingsScreen(
                    repository = repository,
                    currentThemeMode = currentThemeMode,
                    onThemeModeChanged = onThemeModeChanged
                )
            }
        }
    }
}
