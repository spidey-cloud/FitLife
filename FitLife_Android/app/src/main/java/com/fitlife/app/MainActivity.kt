package com.fitlife.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitlife.app.data.food.FoodRepository
import com.fitlife.app.service.StepCounterService
import com.fitlife.app.ui.screens.*
import com.fitlife.app.ui.theme.FitLifeTheme
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Today     : Screen("today",     "Today",     Icons.Outlined.Home)
    object Steps     : Screen("steps",     "Steps",     Icons.Outlined.DirectionsWalk)
    object Workout   : Screen("workout",   "Workout",   Icons.Outlined.FitnessCenter)
    object Cycling   : Screen("cycling",   "Cycling",   Icons.Outlined.Explore)
    object Nutrition : Screen("nutrition", "Nutrition", Icons.Outlined.Restaurant)
}

val navItems = listOf(Screen.Today, Screen.Steps, Screen.Workout, Screen.Cycling, Screen.Nutrition)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var foodRepository: FoodRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitLifeTheme {
                FitLifeRoot(foodRepository)
            }
        }
    }
}

@Composable
fun FitLifeRoot(foodRepo: FoodRepository) {
    val vm: FitnessViewModel = hiltViewModel()
    val profileState by vm.profileState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val prefs = remember { context.getSharedPreferences("fitlife_prefs", Context.MODE_PRIVATE) }
    var permissionsDone by remember {
        mutableStateOf(
            prefs.getBoolean("permissions_completed", false) ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(Unit) {
        vm.message.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    when {
        !permissionsDone -> {
            PermissionScreen(
                onAllGranted = {
                    permissionsDone = true
                    prefs.edit().putBoolean("permissions_completed", true).apply()
                    StepCounterService.start(context)
                }
            )
        }
        !profileState.onboardingComplete -> {
            OnboardingScreen(vm)
        }
        else -> {
            MainApp(vm, foodRepo, snackbarHostState, context)
        }
    }
}

@Composable
private fun MainApp(
    vm: FitnessViewModel,
    foodRepo: FoodRepository,
    snackbarHostState: SnackbarHostState,
    context: Context
) {
    val screenBackStack = remember { mutableStateListOf<Screen>(Screen.Today) }
    val currentScreen = screenBackStack.lastOrNull() ?: Screen.Today
    var showProfile by remember { mutableStateOf(false) }

    BackHandler(enabled = showProfile || screenBackStack.size > 1) {
        if (showProfile) {
            showProfile = false
        } else if (screenBackStack.size > 1) {
            screenBackStack.removeAt(screenBackStack.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        StepCounterService.start(context)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(92.dp),
                containerColor = Color(0xFF000000),
                tonalElevation = 0.dp
            ) {
                navItems.forEach { screen ->
                    val selected = currentScreen == screen && !showProfile
                    NavigationBarItem(
                        icon = {
                            val emoji = when(screen) {
                                Screen.Today     -> "🏠"
                                Screen.Steps     -> "🚶"
                                Screen.Workout   -> "🏋️"
                                Screen.Cycling   -> "🚴"
                                Screen.Nutrition -> "🥗"
                            }
                            Text(emoji, fontSize = 26.sp)
                        },
                        label = {
                            Text(
                                screen.label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        selected = selected,
                        onClick = {
                            showProfile = false
                            if (currentScreen != screen) {
                                screenBackStack.remove(screen)
                                screenBackStack.add(screen)
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFCCFF00),
                            unselectedIconColor = Color(0xFFA1A1AA),
                            selectedTextColor = Color(0xFFCCFF00),
                            unselectedTextColor = Color(0xFFA1A1AA),
                            indicatorColor = Color(0xFF18181B)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().background(Color(0xFF000000))) {
            if (showProfile) {
                ProfileScreen(vm, onBack = { showProfile = false }, contentPadding = innerPadding)
            } else {
                when (currentScreen) {
                    Screen.Today     -> TodayScreen(vm, foodRepo, onProfileClick = { showProfile = true }, contentPadding = innerPadding)
                    Screen.Steps     -> StepsScreen(vm, contentPadding = innerPadding)
                    Screen.Workout   -> WorkoutScreen(vm, contentPadding = innerPadding)
                    Screen.Cycling   -> CyclingScreen(vm, onProfileClick = { showProfile = true }, contentPadding = innerPadding)
                    Screen.Nutrition -> CaloriesScreen(vm, foodRepo, contentPadding = innerPadding)
                }
            }
        }
    }
}
