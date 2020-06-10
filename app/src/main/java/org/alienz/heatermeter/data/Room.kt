package org.alienz.heatermeter.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*
import com.google.gson.Gson
import java.time.Duration
import java.time.Instant

data class Temperature(@ColumnInfo(name = "temperature") val degrees: Double)

inline class DegreesPerHour(val dph: Double)

data class FanSpeed(@ColumnInfo(name = "fan_speed") val rpm: Double)

data class Probe(
    val index: Int,
    val temperature: Temperature,
    val degreesPerHour: DegreesPerHour
)

@Entity(tableName = "samples")
data class Sample(
    @PrimaryKey @ColumnInfo(name = "time") val time: Instant,
    @Embedded(prefix = "set_point_") val setPoint: Temperature,
    @ColumnInfo(name = "lid_open") val lidOpen: Boolean,
    @Embedded val fan: FanSpeed,
    @ColumnInfo(name = "probes") val probes: Array<Probe>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Sample

        if (time != other.time) return false
        if (setPoint != other.setPoint) return false
        if (lidOpen != other.lidOpen) return false
        if (fan != other.fan) return false
        if (!probes.contentEquals(other.probes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = time.hashCode()
        result = 31 * result + setPoint.hashCode()
        result = 31 * result + lidOpen.hashCode()
        result = 31 * result + fan.hashCode()
        result = 31 * result + probes.contentHashCode()
        return result
    }
}

@Entity(tableName = "probe_names")
data class ProbeName(
    @PrimaryKey val index: Int,
    val name: String?
)

class Converters {
    @TypeConverter
    fun instantToLong(instant: Instant): Long {
        return instant.toEpochMilli()
    }
    @TypeConverter
    fun longToInstant(instant: Long): Instant = Instant.ofEpochMilli(instant)

    @TypeConverter
    fun probesToString(probes: Array<Probe>): String {
        return gson.toJson(probes)
    }

    @TypeConverter
    fun stringToProbes(probes: String): Array<Probe> {
        return gson.fromJson(probes, Array<Probe>::class.java)
    }

    companion object {
        private val gson = Gson()
            .newBuilder()
            .serializeSpecialFloatingPointValues()
            .create()
    }
}

@Dao
abstract class SamplesDao {
    @Query("SELECT * FROM samples WHERE time >= (:threshold) ORDER BY time ASC")
    protected abstract fun recent(threshold: Long): LiveData<List<Sample>>

    fun recent(threshold: Instant): LiveData<List<Sample>> {
        return recent(threshold.toEpochMilli())
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(vararg samples: Sample)

    @Query("DELETE FROM samples WHERE time < (:threshold)")
    protected abstract suspend fun trim(threshold: Long)

    suspend fun trim(threshold: Instant) {
        return trim(threshold.toEpochMilli())
    }
}

@Dao
interface ProbeNamesDao {
    @Query("SELECT * FROM probe_names")
    fun all(): LiveData<List<ProbeName>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg names: ProbeName)
}

@Database(entities = [ProbeName::class, Sample::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun samples(): SamplesDao
    abstract fun names(): ProbeNamesDao

    companion object {
        private var db: AppDatabase? = null
        private val lock = object {}

        fun getInstance(context: Context): AppDatabase {
            synchronized(lock) {
                if (db == null) {
                    db = Room
                        .databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "heater-meter"
                        )
                        .fallbackToDestructiveMigration()
                        .build()
                }

                return db!!
            }
        }
    }
}
