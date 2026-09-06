package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bdalxy.edencourt.data.local.entity.WorldInfoBook
import com.bdalxy.edencourt.data.local.entity.WorldInfoEntry
import kotlinx.coroutines.flow.Flow

/**
 * 世界书数据访问（v1.5-A3）。
 * 阅读侧：按书取条目（注入检索用）；写侧：管理页新建/编辑/删除条目与书（UI 属 B3，此处提供完整 CRUD）。
 */
@Dao
interface WorldInfoDao {

    // ---------- 书 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBook(book: WorldInfoBook): Long

    @Update
    fun updateBook(book: WorldInfoBook)

    @Delete
    fun deleteBook(book: WorldInfoBook)

    /**
     * 事务级联删除整本世界书：同一事务内先删全部条目（清理孤儿数据）、再清掉
     * 会话上挂在本书的 worldBookId 引用、最后删书本身。任一步失败整体回滚，
     * 避免「条目残留 / 会话悬挂引用 / 书已删条目未清」的数据不一致。
     * （世界书表与 conversations 表均无外键级联，故需显式清理。）
     */
    @Query("UPDATE conversations SET worldBookId = NULL WHERE worldBookId = :bookId")
    fun clearConversationWorldBook(bookId: Long)

    @Transaction
    fun deleteBookCascade(book: WorldInfoBook) {
        getEntriesByBook(book.id).forEach { deleteEntry(it) }
        clearConversationWorldBook(book.id)
        deleteBook(book)
    }

    @Query("SELECT * FROM world_info_book WHERE id = :bookId")
    fun getBook(bookId: Long): WorldInfoBook?

    @Query("SELECT * FROM world_info_book ORDER BY id")
    fun observeBooks(): Flow<List<WorldInfoBook>>

    @Query("SELECT COUNT(*) FROM world_info_entry WHERE bookId = :bookId")
    fun countEntries(bookId: Long): Int

    // ---------- 条目 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEntry(entry: WorldInfoEntry): Long

    @Update
    fun updateEntry(entry: WorldInfoEntry)

    @Delete
    fun deleteEntry(entry: WorldInfoEntry)

    @Query("SELECT * FROM world_info_entry WHERE id = :id")
    fun getEntry(id: Long): WorldInfoEntry?

    /** 取某书全部条目（注入检索 + 管理页列表） */
    @Query("SELECT * FROM world_info_entry WHERE bookId = :bookId")
    fun getEntriesByBook(bookId: Long): List<WorldInfoEntry>
}