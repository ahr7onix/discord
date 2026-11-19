package com.chronos.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronos.app.domain.model.EventOccurrence
import com.chronos.app.domain.model.OccurrenceStatus
import com.chronos.app.domain.recurrence.DateTimeFmt
import com.chronos.app.domain.recurrence.RecurrenceDescriber
import com.chronos.app.ui.theme.Overdue

/**
 * One occurrence in a list: time column, title + recurrence subtitle, a
 * category accent, and a completion toggle. Overdue pending items get a red
 * time; completed items get strike-through.
 */
@Composable
fun OccurrenceCard(
    occurrence: EventOccurrence,
    categoryColor: Color?,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val completed = occurrence.status == OccurrenceStatus.COMPLETED
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.width(56.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = occurrence.start.toLocalTime().format(DateTimeFmt.time24),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (occurrence.isOverdue) Overdue else MaterialTheme.colorScheme.primary,
                )
                occurrence.end?.let {
                    Text(
                        text = it.toLocalTime().format(DateTimeFmt.time24),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (categoryColor != null) CategoryDot(categoryColor, size = 10)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = occurrence.event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (completed) TextDecoration.LineThrough else null,
                )
                val subtitle = when {
                    occurrence.event.isRecurring -> RecurrenceDescriber.describe(occurrence.event.rule)
                    !occurrence.event.description.isNullOrBlank() -> occurrence.event.description!!
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            CompleteToggle(completed = completed, onToggle = onToggleComplete)
        }
    }
}
