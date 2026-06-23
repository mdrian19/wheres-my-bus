package ro.unitbv.wheresmybus.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites", primaryKeys = ["userId", "stopName"])
data class FavoriteEntity(
    val userId: String,
    val stopName: String
)

@Dao
interface FavoriteDao{
    @Query("SELECT stopName FROM favorites WHERE userId = :userId")
    fun getFavoritesFlow(userId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE userId = :userId AND stopName = :stopName")
    suspend fun deleteFavorite(userId: String, stopName: String)

    @Query("DELETE FROM favorites WHERE userId = :userId")
    suspend fun clearUserFavorites(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoriteEntity>)
}

@Database(entities = [FavoriteEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase: RoomDatabase()
{
    abstract fun favoriteDao(): FavoriteDao
}

object DatabaseProvider{
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase{
        return INSTANCE ?: synchronized(this){
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "wheresmybus_database"
            ).build()
            INSTANCE = instance
            instance
        }
    }
}

