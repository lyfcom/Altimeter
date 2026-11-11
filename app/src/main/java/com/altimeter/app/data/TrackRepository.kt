package com.altimeter.app.data

import androidx.room.withTransaction
import com.altimeter.core.*
import java.io.Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrackPreview(
    val points: List<TrackPoint>,
    val breakBefore: Set<Long>,
    val hasCoordinates: Boolean,
)

class TrackRepository(private val database: TrackDatabase) {
    private val dao = database.tracks()
    val trips = dao.observeTrips()

    suspend fun recoverInterrupted() = dao.recoverInterrupted()

    suspend fun create(trip: TripEntity) = dao.insertTrip(trip)

    suspend fun append(trip: TripEntity, point: TrackPoint) =
        database.withTransaction {
            dao.insertSample(SampleEntity.from(trip.id, point))
            dao.updateTrip(trip)
        }

    suspend fun finish(trip: TripEntity) = dao.updateTrip(trip)

    suspend fun delete(id: String) = dao.deleteTrip(id)

    /**
     * Bounded preview retaining bucket extrema, endpoints and time order; exports remain lossless.
     */
    suspend fun points(id: String): TrackPreview =
        withContext(Dispatchers.Default) {
            val bucketSize = (dao.countSamples(id) / 300).coerceAtLeast(1)
            val result = mutableListOf<TrackPoint>()
            val bucket = mutableListOf<TrackPoint>()
            val breaks = mutableSetOf<Long>()
            var previous: TrackPoint? = null
            fun flush() {
                if (bucket.isEmpty()) return
                result.addAll(
                    listOf(
                            bucket.first(),
                            bucket.minBy { it.reading.metres },
                            bucket.maxBy { it.reading.metres },
                            bucket.last(),
                        )
                        .distinctBy { it.elapsedMillis }
                        .sortedBy { it.elapsedMillis }
                )
                bucket.clear()
            }
            forEachPage(id) { page ->
                page.forEach { sample ->
                    val point = sample.toPoint()
                    val old = previous
                    if (
                        old != null &&
                            (point.elapsedMillis - old.elapsedMillis > 30_000 ||
                                point.reading.reference != old.reading.reference ||
                                point.reading.referenceId != old.reading.referenceId)
                    ) {
                        flush()
                        breaks.add(point.elapsedMillis)
                    }
                    bucket.add(point)
                    if (bucket.size >= bucketSize) flush()
                    previous = point
                }
            }
            flush()
            TrackPreview(result, breaks, dao.hasCoordinates(id))
        }

    suspend fun export(id: String, gpx: Boolean, writer: Writer) {
        if (gpx) {
            GpxWriter(writer).use { output ->
                forEachPage(id) { page -> page.forEach { output.append(it.toPoint()) } }
            }
        } else {
            var headerWritten = false
            forEachPage(id) { page ->
                val buffer = java.io.StringWriter()
                TrackExporter.csv(page.asSequence().map(SampleEntity::toPoint), buffer)
                val text = buffer.toString()
                writer.write(if (headerWritten) text.substringAfter('\n') else text)
                headerWritten = true
            }
            if (!headerWritten) TrackExporter.csv(emptySequence(), writer)
        }
    }

    private suspend fun forEachPage(id: String, consume: (List<SampleEntity>) -> Unit) {
        var cursor = 0L
        while (true) {
            val page = dao.page(id, cursor)
            if (page.isEmpty()) return
            consume(page)
            cursor = page.last().id
        }
    }
}
