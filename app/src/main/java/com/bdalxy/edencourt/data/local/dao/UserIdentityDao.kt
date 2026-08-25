package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bdalxy.edencourt.data.local.entity.UserIdentity
import kotlinx.coroutines.flow.Flow

/**
 * 用户自设身份表数据访问（v1.2b，T8.13）。
 * 唯一一条（id=1），upsert 覆盖即为"唯一绑定"。
 */
@Dao
interface UserIdentityDao {

    /** 观察当前用户身份（无则 null，UI 实时刷新） */
    @Query("SELECT * FROM user_identity WHERE id = 1")
    fun observe(): Flow<UserIdentity?>

    /** 一次性取当前用户身份（无则 null） */
    @Query("SELECT * FROM user_identity WHERE id = 1")
    suspend fun get(): UserIdentity?

    /** 保存/覆盖用户身份（id=1 唯一） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(identity: UserIdentity)
}
