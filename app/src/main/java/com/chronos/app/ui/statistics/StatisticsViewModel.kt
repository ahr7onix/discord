package com.chronos.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.repository.ChronosRepository
import com.chronos.app.domain.model.Category
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class StatsPeriod(val label: String) {
    WEEK("Semana"), MONTH("Mês"), YEAR("Ano"), ALL("Tudo")
}

data class CategoryStat(val category: Category?, val count: Int)

data class StatisticsUiState(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val completedTotal: Int = 0,
    val byCategory: List<CategoryStat> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(private val repository: ChronosRepository) : ViewModel() {

    private val _period = MutableStateFlow(StatsPeriod.MONTH)
    val period = _period.asStateFlow()

    val state = _period.flatMapLatest { period ->
        val (from, to) = rangeFor(period)
        combine(
            repository.completedCount(from, to),
            repository.completedByCategory(from, to),
            repository.categories,
        ) { total, byCat, categories ->
            val catMap = categories.associateBy { it.id }
            StatisticsUiState(
                period = period,
                completedTotal = total,
                byCategory = byCat
                    .map { CategoryStat(it.categoryId?.let(catMap::get), it.total) }
                    .sortedByDescending { it.count },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    fun setPeriod(p: StatsPeriod) { _period.value = p }

    private fun rangeFor(period: StatsPeriod): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (period) {
            StatsPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) to today
            StatsPeriod.MONTH -> today.withDayOfMonth(1) to today
            StatsPeriod.YEAR -> today.withDayOfYear(1) to today
            StatsPeriod.ALL -> LocalDate.ofEpochDay(0) to today
        }
    }
}
