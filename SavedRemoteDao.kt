package com.akillikumanda.app.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedRemoteDao {
    @Query("SELECT * FROM saved_remotes ORDER BY favorite DESC, COALESCE(lastUsedAt, 0) DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SavedRemoteEntity>>

    @Query("SELECT * FROM saved_remotes WHERE id = :id")
    fun observe(id: Long): Flow<SavedRemoteEntity?>

    @Query("SELECT * FROM saved_remotes WHERE id = :id")
    suspend fun get(id: Long): SavedRemoteEntity?

    @Query("SELECT * FROM saved_remotes")
    suspend fun getAll(): List<SavedRemoteEntity>

    @Query("SELECT * FROM saved_remotes WHERE profileId = :profileId AND normalizedName = :normalizedName LIMIT 1")
    suspend fun findDuplicate(profileId: String, normalizedName: String): SavedRemoteEntity?

    @Query("SELECT DISTINCT profileId FROM saved_remotes WHERE verification = 'USER_CONFIRMED'")
    suspend fun confirmedProfileIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SavedRemoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: SavedRemoteEntity): Long

    @Update
    suspend fun update(entity: SavedRemoteEntity)

    @Delete
    suspend fun delete(entity: SavedRemoteEntity)

    @Query("UPDATE saved_remotes SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE saved_remotes SET lastUsedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("UPDATE saved_remotes SET verification = :verification WHERE id = :id")
    suspend fun setVerification(id: Long, verification: String)
}
