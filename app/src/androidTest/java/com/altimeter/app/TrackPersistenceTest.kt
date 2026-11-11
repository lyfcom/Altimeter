package com.altimeter.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.altimeter.app.data.*
import com.altimeter.core.*
import java.io.StringWriter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackPersistenceTest {
    private lateinit var db: TrackDatabase
    private lateinit var repository: TrackRepository

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    TrackDatabase::class.java,
                )
                .build()
        repository = TrackRepository(db)
    }

    @After fun close() = db.close()

    @Test
    fun interruptedSessionRecoversAndDeletionCascades() = runBlocking {
        val trip = TripEntity("trip", "Test", 1000)
        repository.create(trip)
        val point =
            TrackPoint(
                2000,
                100,
                Reading(20.0, Source.BAROMETER, HeightReference.RELATIVE, null, 100),
            )
        repository.append(trip.copy(sampleCount = 1, durationMillis = 1000), point)
        // Wall time moved backwards; recovery must use insertion order, not MAX(time).
        repository.append(
            trip.copy(sampleCount = 2, durationMillis = 2000),
            point.copy(timeMillis = 1500, elapsedMillis = 1100),
        )
        repository.delete(trip.id) // An active session must not be deletable.
        assertEquals(2, db.tracks().countSamples(trip.id))
        repository.recoverInterrupted()
        val recovered = repository.trips.first().single()
        assertEquals(TripStatus.INTERRUPTED, recovered.status)
        assertEquals(1500L, recovered.endedAt)
        assertEquals(2000L, recovered.durationMillis)
        assertEquals(2, recovered.sampleCount)
        repository.delete(trip.id)
        assertEquals(0, db.tracks().countSamples(trip.id))
    }

    @Test
    fun pagedExportsKeepAllSamplesAndPreviewPreservesExtrema() = runBlocking {
        val trip = TripEntity("long", "Long trip", 1000)
        repository.create(trip)
        repeat(1301) { i ->
            val point =
                TrackPoint(
                    1000 + i * 1000L,
                    i * 1000L,
                    Reading(
                        if (i == 611) 1234.0 else 10.0,
                        Source.BAROMETER,
                        HeightReference.RELATIVE,
                        null,
                        i * 1000L,
                    ),
                    latitude = 30.0,
                    longitude = 120.0,
                )
            repository.append(trip.copy(sampleCount = i + 1), point)
        }
        val csv = StringWriter()
        repository.export(trip.id, false, csv)
        assertEquals(1302, csv.toString().trim().lines().size)
        val gpx = StringWriter()
        repository.export(trip.id, true, gpx)
        assertEquals(1301, "<trkpt ".toRegex().findAll(gpx.toString()).count())
        assertFalse(gpx.toString().contains("<ele>"))
        val preview = repository.points(trip.id)
        assertTrue(preview.points.size < 1301)
        assertEquals(1234.0, preview.points.maxOf { it.reading.metres }, 0.0)
    }
}
