package net.alextaran.altimeter.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AltitudeDao {
    @Insert
    suspend fun insertPoint(point: AltitudePoint)

    @Query("SELECT * FROM altitude_points WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getPointsSince(sinceTimestamp: Long): Flow<List<AltitudePoint>>
}