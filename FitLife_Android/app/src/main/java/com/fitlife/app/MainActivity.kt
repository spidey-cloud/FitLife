package com.fitlife.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitlife.app.data.food.FoodRepository
import com.fitlife.app.service.StepCounterService
import com.fitlife.app.ui.screens.*
import com.fitlife.app.ui.theme.FitLifeTheme
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import com.fitlife.app.ui.viewmodel.RideViewModel
import com.fitlife.app.ui.viewmodel.UiMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════════════════
// ROUTES
// ═══════════════════════════════════════════════════════════════════════

object Routes {
    const val TODAY = "today"
    const val ACTIVITY = "activity"
    const val WORKOUT = "workout"
    const val RIDE = "ride"
    const val NUTRITION = "nutrition"
    const val WELLBEING = "wellbeing"
    const val PLANS = "plans"
    const val PROFILE = "profile"
    const val RIDE_LIVE = "ride/live"
    const val RIDE_SUMMARY = "ride/summary/{id}?saved={saved}"
    fun rideSummary(id: Long, saved: Boolean) = "ride/summary/$id?saved=$saved"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab(Routes.TODAY, "Today", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.ACTIVITY, "Activity", Icons.AutoMirrored.Outlined.DirectionsWalk, Icons.AutoMirrored.Filled.DirectionsWalk),
    Tab(Routes.WORKOUT, "Workout", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
    Tab(Routes.RIDE, "Ride", Icons.AutoMirrored.Outlined.DirectionsBike, Icons.AutoMirrored.Filled.DirectionsBike),
    Tab(Routes.NUTRITION, "Nutrition", Icons.Outlined.Restaurant, Icons.Filled.Restaurant),
)

// ═══════════════════════════════════════════════════════════════════════
// ACTIVITY
// ═══════════════════════════════════════════════════════════════════════

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var foodRepository: FoodRepository

    /** Emits when the ride notification is tapped (activity is singleTop). */
    private val openRide = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launchedForRide = intent?.action == ACTION_OPEN_RIDE
        setContent {
            val vm: FitnessViewModel = hiltViewModel()
            val profile by vm.profileState.collectAsState()
            FitLifeTheme(themeMode = profile.profile.themeMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FitLifeRoot(vm, foodRepository, openRide.asSharedFlow(), launchedForRide)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_RIDE) openRide.tryEmit(Unit)
    }

    companion object {
        const val ACTION_OPEN_RIDE = "com.fitlife.app.OPEN_RIDE"
    }
}

@Composable
private fun FitLifeRoot(
    vm: FitnessViewModel,
    foodRepo: FoodRepository,
    openRide: SharedFlow<Unit>,
    launchedForRide: Boolean
) {
    val profileState by vm.profileState.collectAsState()
    var permissionsDone by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    when {
        !profileState.loaded -> Box(Modifier.fillMaxSize())     // first DB read; avoids flashing onboarding
        !profileState.onboardingComplete -> OnboardingScreen(vm)
        !permissionsDone && !launchedForRide -> PermissionScreen(onDone = {
            permissionsDone = true
            StepCounterService.start(context)
        })
        else -> {
            LaunchedEffect(Unit) { StepCounterService.start(context) }
            MainApp(vm, foodRepo, openRide, launchedForRide)
        }
    }
}

@Composable
private fun MainApp(
    vm: FitnessViewModel,
    foodRepo: FoodRepository,
    openRide: SharedFlow<Unit>,
    launchedForRide: Boolean
) {
    val rideVm: RideViewModel = hiltViewModel()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isHud = route == Routes.RIDE_LIVE
    val showBottomBar = route in TABS.map { it.route }

    // One snackbar host for both view models; messages may carry an Undo action.
    LaunchedEffect(Unit) {
        merge(vm.message, rideVm.messages).collectLatest { msg: UiMessage ->
            val result = snackbar.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                withDismissAction = msg.actionLabel == null,
                duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) msg.onAction?.invoke()
        }
    }
    val say: (String) -> Unit = { text -> if (text.isNotBlank()) vm.toast(text) }

    // Ride notification → live HUD (only if a ride is actually recording).
    LaunchedEffect(Unit) {
        if (launchedForRide && rideVm.session.value.isActive) nav.navigate(Routes.RIDE_LIVE) { launchSingleTop = true }
        openRide.collect {
            if (rideVm.session.value.isActive) nav.navigate(Routes.RIDE_LIVE) { launchSingleTop = true }
            else nav.navigateTab(Routes.RIDE)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                TABS.forEach { tab ->
                    val selected = route == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.navigateTab(tab.route) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { inner ->
        // The HUD draws edge-to-edge and handles its own insets.
        val padding = if (isHud) PaddingValues(0.dp) else inner
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.padding(padding)) {
            composable(Routes.TODAY) {
                TodayScreen(
                    vm = vm, rideVm = rideVm,
                    onOpenActivity = { nav.navigateTab(Routes.ACTIVITY) },
                    onOpenWorkout = { nav.navigateTab(Routes.WORKOUT) },
                    onOpenNutrition = { nav.navigateTab(Routes.NUTRITION) },
                    onOpenRide = { nav.navigateTab(Routes.RIDE) },
                    onOpenLiveRide = { nav.navigate(Routes.RIDE_LIVE) { launchSingleTop = true } },
                    onOpenWellbeing = { nav.navigate(Routes.WELLBEING) },
                    onOpenProfile = { nav.navigate(Routes.PROFILE) }
                )
            }
            composable(Routes.ACTIVITY) { ActivityScreen(vm) }
            composable(Routes.WORKOUT) { WorkoutScreen(vm) }
            composable(Routes.RIDE) {
                CyclingScreen(
                    rideVm = rideVm,
                    onOpenLive = { nav.navigate(Routes.RIDE_LIVE) { launchSingleTop = true } },
                    onOpenSummary = { id, saved -> nav.navigate(Routes.rideSummary(id, saved)) },
                    onMessage = say
                )
            }
            composable(Routes.NUTRITION) { NutritionScreen(vm, foodRepo, onOpenPlans = { nav.navigate(Routes.PLANS) }) }
            composable(Routes.WELLBEING) { WellbeingScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.PLANS) { DietPlansScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.PROFILE) {
                ProfileScreen(vm, healthConnectAvailable = rideVm.healthConnectAvailable, onBack = { nav.popBackStack() })
            }
            composable(Routes.RIDE_LIVE) {
                ActiveRideScreen(
                    rideVm = rideVm,
                    onMinimize = { if (!nav.popBackStack()) nav.navigateTab(Routes.RIDE) },
                    onSaved = { saved ->
                        nav.navigate(Routes.rideSummary(saved.id, true)) {
                            popUpTo(Routes.RIDE_LIVE) { inclusive = true }
                        }
                    },
                    onEnded = { text ->
                        say(text)
                        if (!nav.popBackStack()) nav.navigateTab(Routes.RIDE)
                    }
                )
            }
            composable(
                Routes.RIDE_SUMMARY,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("saved") { type = NavType.BoolType; defaultValue = false }
                )
            ) { entry ->
                RideSummaryScreen(
                    rideVm = rideVm,
                    rideId = entry.arguments?.getLong("id") ?: 0L,
                    justSaved = entry.arguments?.getBoolean("saved") ?: false,
                    onBack = { if (!nav.popBackStack()) nav.navigateTab(Routes.RIDE) }
                )
            }
        }
    }
}

/** Standard bottom-tab navigation: single top, restore state, don't stack tabs. */
private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
