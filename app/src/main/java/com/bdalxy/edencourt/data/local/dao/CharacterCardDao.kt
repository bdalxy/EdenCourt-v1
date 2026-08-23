package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import kotlinx.coroutines.flow.Flow

/**
 * 角色卡表数据访问（企划书 4.1.3 / T4.2）。
 * Flow 查询为 Room 响应式查询：character_cards 表变化时自动重查重发。
 */
@Dao
interface CharacterCardDao {

    /** 新增角色卡，返回生成的 id */
    @Insert
    suspend fun insert(card: CharacterCardEntity): Long

    /** 更新角色卡（编辑名称/描述后刷新 updatedAt） */
    @Update
    suspend fun update(card: CharacterCardEntity)

    /** 删除角色卡（调用方需先校验非内置卡） */
    @Delete
    suspend fun delete(card: CharacterCardEntity)

    /** 观察全部角色卡：内置卡优先，再按创建时间降序 */
    @Query("SELECT * FROM character_cards ORDER BY isBuiltIn DESC, createdAt DESC")
    fun observeAll(): Flow<List<CharacterCardEntity>>

    /** 按 id 观察单张角色卡（详情页实时跟随） */
    @Query("SELECT * FROM character_cards WHERE id = :id")
    fun observeById(id: Long): Flow<CharacterCardEntity?>

    /** 按 id 取单张角色卡（一次性查询） */
    @Query("SELECT * FROM character_cards WHERE id = :id")
    suspend fun getById(id: Long): CharacterCardEntity?

    /** 内置卡数量（首次启动播种内置卡时判断是否已插入） */
    @Query("SELECT COUNT(*) FROM character_cards WHERE isBuiltIn = 1")
    suspend fun countBuiltIn(): Int
}
