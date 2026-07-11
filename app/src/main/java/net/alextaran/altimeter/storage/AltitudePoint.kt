package net.alextaran.altimeter.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "altitude_points")
data class AltitudePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val altitude: Double
)