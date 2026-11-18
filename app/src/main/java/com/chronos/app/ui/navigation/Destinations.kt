package com.chronos.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level, bottom-bar destinations plus the modal edit route. */
sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Calendar : Dest("calendar")
    data object Events : Dest("events")
    data object Statistics : Dest("statistics")
    data object Settings : Dest("settings")

    data object EditEvent : Dest("edit?eventId={eventId}&day={day}") {
        fun build(eventId: Long? = null, epochDay: Long? = null): String {
            val id = eventId?.toString() ?: "-1"
            val day = epochDay?.toString() ?: "-1"
            return "edit?eventId=$id&day=$day"
        }
    }
}

data class BottomItem(val dest: Dest, val label: String, val icon: ImageVector)

val bottomItems = listOf(
    BottomItem(Dest.Home, "Início", Icons.Filled.Home),
    BottomItem(Dest.Calendar, "Calendário", Icons.Filled.CalendarMonth),
    BottomItem(Dest.Events, "Agenda", Icons.AutoMirrored.Filled.List),
    BottomItem(Dest.Statistics, "Estatísticas", Icons.Filled.BarChart),
    BottomItem(Dest.Settings, "Ajustes", Icons.Filled.Settings),
)
