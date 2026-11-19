package com.chronos.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.chronos.app.ui.ChronosViewModelFactory
import com.chronos.app.ui.calendar.CalendarScreen
import com.chronos.app.ui.calendar.CalendarViewModel
import com.chronos.app.ui.events.EditEventScreen
import com.chronos.app.ui.events.EditEventViewModel
import com.chronos.app.ui.events.EventDetailScreen
import com.chronos.app.ui.events.EventDetailViewModel
import com.chronos.app.ui.events.EventsScreen
import com.chronos.app.ui.events.EventsViewModel
import com.chronos.app.ui.home.HomeScreen
import com.chronos.app.ui.home.HomeViewModel
import com.chronos.app.ui.settings.SettingsScreen
import com.chronos.app.ui.settings.SettingsViewModel
import com.chronos.app.ui.statistics.StatisticsScreen
import com.chronos.app.ui.statistics.StatisticsViewModel

@Composable
fun ChronosNavHost(
    navController: NavHostController,
    factory: ChronosViewModelFactory,
    startDestination: String = Dest.Splash.route,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val openEdit: (Long?, Long?) -> Unit = { id, day ->
        navController.navigate(Dest.EditEvent.build(id, day))
    }
    val openDay: (Long) -> Unit = { day -> navController.navigate(Dest.EditEvent.build(null, day)) }
    val openDetail: (Long) -> Unit = { id -> navController.navigate(Dest.EventDetail.build(id)) }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(Dest.Splash.route) {
            com.chronos.app.ui.splash.SplashScreen(
                onFinished = {
                    navController.navigate(Dest.Home.route) {
                        popUpTo(Dest.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Dest.Home.route) {
            val vm: HomeViewModel = viewModel(factory = factory)
            HomeScreen(vm, onOpenEvent = openDetail, onNewEvent = { openEdit(null, null) })
        }
        composable(Dest.Calendar.route) {
            val vm: CalendarViewModel = viewModel(factory = factory)
            CalendarScreen(vm, onOpenEvent = openDetail, onNewOnDay = { openDay(it) })
        }
        composable(Dest.Events.route) {
            val vm: EventsViewModel = viewModel(factory = factory)
            EventsScreen(vm, onOpenEvent = openDetail)
        }
        composable(Dest.Statistics.route) {
            val vm: StatisticsViewModel = viewModel(factory = factory)
            StatisticsScreen(vm)
        }
        composable(Dest.Settings.route) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(vm)
        }
        composable(
            route = Dest.EventDetail.route,
            arguments = listOf(navArgument("eventId") { type = NavType.LongType }),
        ) { entry ->
            val eventId = entry.arguments?.getLong("eventId") ?: -1L
            val vm: EventDetailViewModel = viewModel(factory = factory.forEventDetail(eventId))
            EventDetailScreen(
                vm,
                onEdit = { openEdit(it, null) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Dest.EditEvent.route,
            arguments = listOf(
                navArgument("eventId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("day") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val eventId = entry.arguments?.getLong("eventId")?.takeIf { it >= 0 }
            val day = entry.arguments?.getLong("day")?.takeIf { it >= 0 }
            val vm: EditEventViewModel = viewModel(factory = factory.forEditEvent(eventId, day))
            EditEventScreen(vm, onDone = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
        }
    }
}
