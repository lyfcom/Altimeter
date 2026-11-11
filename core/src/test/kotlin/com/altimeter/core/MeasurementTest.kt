package com.altimeter.core

import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class MeasurementTest {
    @Test
    fun pressureCalibrationRoundTrips() {
        for (height in listOf(-400.0, 0.0, 120.0, 2000.0, 6000.0)) {
            val pressure = 1013.25 * Math.pow(1 - height / 44330, 5.255)
            val reference = Barometry.calibrate(pressure, height)
            assertEquals(height, Barometry.altitude(pressure, reference), 0.000001)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPressure() {
        Barometry.altitude(Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsImpossibleCalibration() {
        Barometry.calibrate(1000.0, 8000.0)
    }

    @Test
    fun calibrationExpiresAndRejectsFutureClock() {
        val cal = Calibration(1013.25, 1000)
        assertTrue(cal.isValid(1000))
        assertFalse(cal.isValid(999))
        assertFalse(cal.isValid(1000 + Calibration.MAX_AGE + 1))
    }

    private fun fix(msl: Double? = null, time: Long = 1000) =
        Fix(30.0, 120.0, 100.0, msl, 12.0, 15.0, 1.0, time)

    @Test
    fun usesVerticalAccuracyNotHorizontalAccuracy() {
        val reading = ReadingPolicy.satellite(fix(), 1001)!!
        assertEquals(12.0, reading.accuracyMetres!!, 0.0)
        assertEquals(HeightReference.ELLIPSOID, reading.reference)
    }

    @Test
    fun usesMslAccuracyOnlyForMslHeight() {
        val reading = ReadingPolicy.satellite(fix(80.0), 1001)!!
        assertEquals(80.0, reading.metres, 0.0)
        assertEquals(15.0, reading.accuracyMetres!!, 0.0)
        assertEquals(HeightReference.SEA_LEVEL, reading.reference)
    }

    @Test
    fun staleAndFutureLocationsAreRejected() {
        assertNull(ReadingPolicy.satellite(fix(), 16001))
        assertNull(ReadingPolicy.satellite(fix(), 999))
    }

    @Test
    fun missingHeightIsNotZero() {
        assertNull(ReadingPolicy.satellite(fix().copy(ellipsoidMetres = null), 1001))
    }

    @Test
    fun uncalibratedPressureDoesNotOverrideSatellite() {
        val gps = ReadingPolicy.satellite(fix(), 1001)
        val pressure = Reading(0.0, Source.BAROMETER, HeightReference.RELATIVE, null, 1001)
        assertEquals(gps, ReadingPolicy.select(MeasureMode.AUTO, gps, pressure))
        assertEquals(pressure, ReadingPolicy.select(MeasureMode.BAROMETER, gps, pressure))
        assertEquals(pressure, ReadingPolicy.select(MeasureMode.AUTO, null, pressure))
    }

    @Test
    fun missingAccuracyIsNotInvented() {
        assertNull(
            ReadingPolicy.satellite(fix().copy(verticalAccuracyMetres = null), 1001)!!
                .accuracyMetres
        )
    }

    @Test
    fun filterRejectsBadEventsAndRecoversAfterPause() {
        val filter = PressureFilter()
        assertEquals(1000.0, filter.add(1000.0, 1000)!!, 0.0)
        assertNull(filter.add(Double.NaN, 1100))
        assertEquals(1000.0, filter.add(800.0, 999)!!, 0.0)
        assertEquals(900.0, filter.add(900.0, 10000)!!, 0.0)
    }

    private fun point(
        h: Double,
        t: Long,
        source: Source = Source.BAROMETER,
        ref: HeightReference = HeightReference.RELATIVE,
        id: Long = 1,
    ) = TrackPoint(t, t, Reading(h, source, ref, if (source == Source.GNSS) 5.0 else null, t, id))

    @Test
    fun stationaryNoiseDoesNotAccumulateClimb() {
        val points = (1..1000).map { point(100.0 + if (it % 2 == 0) .4 else -.4, it * 2000L) }
        val stats = TrackAccumulator.calculate(points)
        assertEquals(0.0, stats.ascent, 0.0)
        assertEquals(0.0, stats.descent, 0.0)
    }

    @Test
    fun steadyClimbAndDescentAccumulate() {
        val stats =
            TrackAccumulator.calculate(
                listOf(point(0.0, 0), point(2.0, 2000), point(4.0, 4000), point(0.0, 6000))
            )
        assertEquals(4.0, stats.ascent, 0.0)
        assertEquals(4.0, stats.descent, 0.0)
    }

    @Test
    fun gapsAndReferenceChangesDoNotCountAsClimb() {
        val stats =
            TrackAccumulator.calculate(
                listOf(
                    point(0.0, 0),
                    point(100.0, 40000),
                    point(200.0, 42000, id = 2),
                    point(300.0, 44000, Source.GNSS, HeightReference.ELLIPSOID),
                )
            )
        assertEquals(0.0, stats.ascent, 0.0)
    }

    @Test
    fun duplicateTimestampIsIgnored() {
        assertEquals(1, TrackAccumulator.calculate(listOf(point(0.0, 1), point(500.0, 1))).count)
    }

    @Test
    fun jumpIsNotRecordedAsClimb() {
        val stats = TrackAccumulator.calculate(listOf(point(0.0, 0), point(1000.0, 2000)))
        assertEquals(0.0, stats.ascent, 0.0)
    }

    @Test
    fun gpxKeepsHeightDatumAndDoesNotInventSeaLevel() {
        val writer = StringWriter()
        TrackExporter.gpx(
            sequenceOf(
                point(50.0, 1000).copy(latitude = 30.0, longitude = 120.0),
                point(60.0, 2000, Source.GNSS, HeightReference.SEA_LEVEL)
                    .copy(latitude = 30.0, longitude = 120.0),
            ),
            writer,
        )
        val xml = writer.toString()
        val doc =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        assertEquals(1, doc.getElementsByTagName("ele").length)
        assertEquals(2, doc.getElementsByTagName("trkseg").length)
        assertTrue(xml.contains("RELATIVE"))
    }

    @Test
    fun csvPreservesPressureOnlyPointsAndUnknownAccuracy() {
        val writer = StringWriter()
        TrackExporter.csv(sequenceOf(point(3.0, 1000)), writer)
        assertEquals(2, writer.toString().trim().lines().size)
        assertTrue(writer.toString().contains("RELATIVE,BAROMETER,,,,,false,1"))
    }
}
