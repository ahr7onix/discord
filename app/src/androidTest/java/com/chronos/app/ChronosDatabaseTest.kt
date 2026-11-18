package com.chronos.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronos.app.data.database.ChronosDatabase
import com.chronos.app.data.repository.ChronosRepository
import com.chronos.app.domain.model.Category
import com.chronos.app.domain.model.Event
import com.chronos.app.domain.model.Frequency
import com.chronos.app.domain.model.OccurrenceStatus
import com.chronos.app.domain.model.RecurrenceRule
import com.chronos.app.domain.scheduler.NoopScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * Instrumented test of the Room stack + repository occurrence expansion.
 * Runs on a device/emulator via `./gradlew connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ChronosDatabaseTest {

    private lateinit var db: ChronosDatabase
    private lateinit var repository: ChronosRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChronosDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ChronosRepository(db.eventDao(), db.categoryDao(), db.historyDao(), NoopScheduler)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun saveAndExpandDailyEvent() = runBlocking {
        val catId = repository.saveCategory(Category(name = "Saúde", colorArgb = 0xFF10B981.toInt()))
        val id = repository.saveEvent(
            Event(
                title = "Remédio", categoryId = catId, startTime = LocalTime.of(8, 0),
                rule = RecurrenceRule(Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1)),
            )
        )
        assertTrue(id > 0)
        val occ = repository.occurrences(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7)).first()
        assertEquals(7, occ.size)
    }

    @Test
    fun skipOccurrence_removesThatDate() = runBlocking {
        val id = repository.saveEvent(
            Event(
                title = "Caminhada", startTime = LocalTime.of(7, 0),
                rule = RecurrenceRule(Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1)),
            )
        )
        repository.skipOccurrence(id, LocalDate.of(2026, 1, 3))
        val occ = repository.occurrences(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)).first()
        assertEquals(4, occ.size)
        assertTrue(occ.none { it.date == LocalDate.of(2026, 1, 3) })
    }

    @Test
    fun markCompleted_reflectsInStatusAndHistory() = runBlocking {
        val event = Event(
            title = "Leitura", startTime = LocalTime.of(21, 0),
            rule = RecurrenceRule(Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1)),
        )
        val id = repository.saveEvent(event)
        val saved = repository.getEvent(id)!!
        repository.markStatus(saved, LocalDate.of(2026, 1, 2), OccurrenceStatus.COMPLETED)

        val occ = repository.occurrences(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 2)).first()
        assertEquals(OccurrenceStatus.COMPLETED, occ.single().status)

        val completed = repository.completedCount(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).first()
        assertEquals(1, completed)
    }
}
