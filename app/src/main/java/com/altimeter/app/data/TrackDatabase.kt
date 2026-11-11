package com.altimeter.app.data

import androidx.room.*
import com.altimeter.core.*
import kotlinx.coroutines.flow.Flow

enum class TripStatus {
    RECORDING,
    COMPLETED,
    INTERRUPTED,
}

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: TripStatus = TripStatus.RECORDING,
    val startedElapsed: Long = 0,
    val durationMillis: Long = 0,
    val sampleCount: Int = 0,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val ascent: Double = 0.0,
    val descent: Double = 0.0,
)

@Entity(
    tableName = "samples",
    foreignKeys =
        [
            ForeignKey(
                entity = TripEntity::class,
                parentColumns = ["id"],
                childColumns = ["tripId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["tripId", "timeMillis"])],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val timeMillis: Long,
    val elapsedMillis: Long,
    val metres: Double,
    val source: String,
    val reference: String,
    val referenceId: Long,
    val accuracyMetres: Double?,
    val pressureHpa: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val mock: Boolean,
) {
    fun toPoint() =
        TrackPoint(
            timeMillis,
            elapsedMillis,
            Reading(
                metres,
                Source.valueOf(source),
                HeightReference.valueOf(reference),
                accuracyMetres,
                elapsedMillis,
                referenceId,
            ),
            pressureHpa,
            latitude,
            longitude,
            mock,
        )

    companion object {
        fun from(tripId: String, point: TrackPoint) =
            SampleEntity(
                tripId = tripId,
                timeMillis = point.timeMillis,
                elapsedMillis = point.elapsedMillis,
                metres = point.reading.metres,
                source = point.reading.source.name,
                reference = point.reading.reference.name,
                referenceId = point.reading.referenceId,
                accuracyMetres = point.reading.accuracyMetres,
                pressureHpa = point.pressureHpa,
                latitude = point.latitude,
                longitude = point.longitude,
                mock = point.mock,
            )
    }
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM trips ORDER BY startedAt DESC") fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT COUNT(*) FROM samples WHERE tripId = :tripId")
    suspend fun countSamples(tripId: String): Int

    @Query("SELECT * FROM samples WHERE tripId = :tripId AND id > :afterId ORDER BY id LIMIT 1000")
    suspend fun page(tripId: String, afterId: Long): List<SampleEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM samples WHERE tripId = :tripId AND latitude IS NOT NULL AND longitude IS NOT NULL)"
    )
    suspend fun hasCoordinates(tripId: String): Boolean

    @Insert suspend fun insertTrip(trip: TripEntity)

    @Insert suspend fun insertSample(sample: SampleEntity)

    @Update suspend fun updateTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id AND status != 'RECORDING'")
    suspend fun deleteTrip(id: String)

    @Query(
        "UPDATE trips SET endedAt = COALESCE((SELECT timeMillis FROM samples WHERE tripId = trips.id ORDER BY id DESC LIMIT 1), startedAt), status = 'INTERRUPTED' WHERE status = 'RECORDING'"
    )
    suspend fun recoverInterrupted()
}

@Database(entities = [TripEntity::class, SampleEntity::class], version = 1, exportSchema = true)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
}
