package com.pbcam.app.data.db

import androidx.room.TypeConverter
import com.pbcam.app.data.CameraSource

class TypeConverters {
    @TypeConverter
    fun fromStatus(status: RecordingStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): RecordingStatus = RecordingStatus.valueOf(value)

    @TypeConverter
    fun fromSource(source: CameraSource): String = source.name

    @TypeConverter
    fun toSource(value: String): CameraSource = CameraSource.valueOf(value)
}
