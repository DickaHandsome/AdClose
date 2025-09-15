package com.close.hook.ads.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.close.hook.ads.data.model.Url
import kotlinx.coroutines.flow.Flow

@Dao
interface UrlDao {

    @Insert
    fun insert(url: Url): Long

    @Query("SELECT * FROM url_info")
    fun findAllList(): List<Url>

    @Query("DELETE FROM url_info WHERE id = :id")
    fun deleteById(id: Long): Int

    @Update
    fun update(url: Url): Int

    @Insert
    fun insertAll(urls: List<Url>): List<Long>

    @Query("SELECT * FROM url_info ORDER BY id DESC")
    fun loadAllList(): Flow<List<Url>>

    @Query("SELECT * FROM url_info WHERE url LIKE '%' || :searchText || '%' OR type LIKE '%' || :searchText || '%' ORDER BY id DESC")
    fun searchUrls(searchText: String): Flow<List<Url>>

    @Query("SELECT * FROM url_info WHERE type = 'URL' AND :fullUrl LIKE url || '%' LIMIT 1")
    fun findUrlMatch(fullUrl: String): Url?

    @Query("SELECT * FROM url_info WHERE type = 'Domain' AND url = :host LIMIT 1")
    fun findDomainMatch(host: String): Url?

    @Query("SELECT * FROM url_info WHERE type = 'KeyWord' AND INSTR(:value, url) > 0 LIMIT 1")
    fun findKeywordMatch(value: String): Url?

    @Query("SELECT COUNT(*) > 0 FROM url_info WHERE type = 'URL' AND :fullUrl LIKE url || '%'")
    fun existsUrlMatch(fullUrl: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM url_info WHERE type = 'Domain' AND url = :host")
    fun existsDomainMatch(host: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM url_info WHERE type = 'KeyWord' AND INSTR(:value, url) > 0")
    fun existsKeywordMatch(value: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM url_info WHERE url = :url")
    fun isExist(url: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM url_info WHERE type = :type AND url = :url")
    fun isExist(type: String, url: String): Boolean

    @Delete
    fun deleteList(list: List<Url>): Int

    @Delete
    fun deleteUrl(url: Url): Int

    @Query("DELETE FROM url_info WHERE type = :type AND url = :url")
    fun deleteUrlString(type: String, url: String): Int

    @Query("DELETE FROM url_info")
    fun deleteAll(): Int
}
