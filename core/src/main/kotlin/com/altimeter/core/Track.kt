package com.altimeter.core

import java.io.Writer
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max

/** Coordinates are optional: pressure-only recordings remain useful indoors. */
data class TrackPoint(
    val timeMillis: Long,
    val elapsedMillis: Long,
    val reading: Reading,
    val pressureHpa: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mock: Boolean = false,
)

data class TrackStats(
    val count: Int = 0,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val ascent: Double = 0.0,
    val descent: Double = 0.0,
)

/**
 * Deadband prevents summing sensor jitter. Gaps and reference/source changes start a new segment.
 */
class TrackAccumulator {
    var stats = TrackStats()
        private set

    private var previous: TrackPoint? = null
    private var anchor: Double? = null

    fun add(point: TrackPoint) {
        val reading = point.reading
        if (!reading.metres.isFinite()) return
        val old = previous
        if (old != null && point.elapsedMillis <= old.elapsedMillis) return
        stats =
            stats.copy(
                count = stats.count + 1,
                minimum = stats.minimum?.let { minOf(it, reading.metres) } ?: reading.metres,
                maximum = stats.maximum?.let { maxOf(it, reading.metres) } ?: reading.metres,
            )
        val newSegment =
            old == null ||
                point.elapsedMillis - old.elapsedMillis > 30_000 ||
                old.reading.source != reading.source ||
                old.reading.reference != reading.reference ||
                old.reading.referenceId != reading.referenceId
        val unreliable = reading.source == Source.GNSS && (reading.accuracyMetres ?: 100.0) > 30
        if (newSegment || unreliable) {
            anchor = if (unreliable) null else reading.metres
        } else {
            val speed =
                abs(reading.metres - old!!.reading.metres) /
                    ((point.elapsedMillis - old.elapsedMillis) / 1000.0)
            if (speed > 25) {
                anchor = null
                previous = point
                return
            }
            val base = anchor
            if (base == null) anchor = reading.metres
            else {
                val threshold =
                    if (reading.source == Source.BAROMETER) 1.5
                    else max(3.0, reading.accuracyMetres ?: 10.0)
                val change = reading.metres - base
                if (abs(change) >= threshold) {
                    stats =
                        stats.copy(
                            ascent = stats.ascent + max(change, 0.0),
                            descent = stats.descent + max(-change, 0.0),
                        )
                    anchor = reading.metres
                }
            }
        }
        previous = point
    }

    companion object {
        fun calculate(points: List<TrackPoint>): TrackStats =
            TrackAccumulator().apply { points.forEach(::add) }.stats
    }
}

object TrackExporter {
    fun csv(points: Sequence<TrackPoint>, writer: Writer) {
        writer.append(
            "time_utc,height_m,reference,source,vertical_accuracy_m,pressure_hpa,latitude,longitude,mock,reference_id\n"
        )
        points.forEach { p ->
            writer
                .append(
                    listOf(
                            Instant.ofEpochMilli(p.timeMillis).toString(),
                            p.reading.metres,
                            p.reading.reference.name,
                            p.reading.source.name,
                            p.reading.accuracyMetres ?: "",
                            p.pressureHpa ?: "",
                            p.latitude ?: "",
                            p.longitude ?: "",
                            p.mock,
                            p.reading.referenceId,
                        )
                        .joinToString(",")
                )
                .append('\n')
        }
    }

    fun gpx(points: Sequence<TrackPoint>, writer: Writer) {
        GpxWriter(writer).use { output -> points.forEach(output::append) }
    }
}

/**
 * Incremental GPX writer, shared by full-sequence and paged exports. Does not own the output
 * stream.
 */
class GpxWriter(private val writer: Writer) : AutoCloseable {
    private var segmentOpen = false
    private var last: TrackPoint? = null

    init {
        writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        writer.append(
            "<gpx version=\"1.1\" creator=\"Altimeter\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:alt=\"https://github.com/lyfcom/Altimeter\"><trk><name>Altimeter</name>"
        )
    }

    fun append(p: TrackPoint) {
        val lat = p.latitude
        val lon = p.longitude
        if (
            lat == null ||
                lon == null ||
                !lat.isFinite() ||
                !lon.isFinite() ||
                lat !in -90.0..90.0 ||
                lon !in -180.0..180.0
        ) {
            endSegment()
            last = null
            return
        }
        val old = last
        if (
            old != null &&
                (p.elapsedMillis - old.elapsedMillis > 30_000 ||
                    p.reading.reference != old.reading.reference ||
                    p.reading.referenceId != old.reading.referenceId)
        )
            endSegment()
        if (!segmentOpen) {
            writer.append("<trkseg>")
            segmentOpen = true
        }
        writer.append("<trkpt lat=\"$lat\" lon=\"$lon\">")
        if (p.reading.reference == HeightReference.SEA_LEVEL)
            writer.append("<ele>${p.reading.metres}</ele>")
        writer.append(
            "<time>${Instant.ofEpochMilli(p.timeMillis)}</time><extensions><alt:height>${p.reading.metres}</alt:height><alt:reference>${p.reading.reference}</alt:reference><alt:source>${p.reading.source}</alt:source><alt:mock>${p.mock}</alt:mock></extensions></trkpt>"
        )
        last = p
    }

    private fun endSegment() {
        if (segmentOpen) writer.append("</trkseg>")
        segmentOpen = false
    }

    override fun close() {
        endSegment()
        writer.append("</trk></gpx>\n")
    }
}
