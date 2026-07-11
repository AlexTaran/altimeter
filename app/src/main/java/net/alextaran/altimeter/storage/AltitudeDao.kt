package net.alextaran.altimeter.storage

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface AltitudeDao {
    @Insert
    suspend fun insertPoint(point: AltitudePoint)
}