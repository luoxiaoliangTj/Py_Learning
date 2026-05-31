package com.tangtang.stockadvisor.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    // Cached stocks
    @Query("SELECT * FROM cached_stocks ORDER BY code")
    fun getAllCachedStocks(): Flow<List<CachedStock>>

    @Query("SELECT * FROM cached_stocks WHERE code = :code")
    suspend fun getCachedStock(code: String): CachedStock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedStock(stock: CachedStock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedStocks(stocks: List<CachedStock>)

    @Query("DELETE FROM cached_stocks WHERE code = :code")
    suspend fun deleteCachedStock(code: String)

    @Query("DELETE FROM cached_stocks")
    suspend fun clearCachedStocks()

    // Watchlist
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getWatchlist(): Flow<List<WatchlistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistItem)

    @Delete
    suspend fun removeFromWatchlist(item: WatchlistItem)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE code = :code)")
    suspend fun isInWatchlist(code: String): Boolean

    // Portfolio cache
    @Query("SELECT * FROM portfolio_cache ORDER BY code")
    fun getPortfolioCache(): Flow<List<PortfolioCache>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolioCache(item: PortfolioCache)

    @Update
    suspend fun updatePortfolioCache(item: PortfolioCache)

    @Query("DELETE FROM portfolio_cache WHERE code = :code")
    suspend fun deletePortfolioCache(code: String)

    @Query("DELETE FROM portfolio_cache")
    suspend fun clearPortfolioCache()
}

@Database(
    entities = [CachedStock::class, WatchlistItem::class, PortfolioCache::class],
    version = 1,
    exportSchema = false
)
abstract class StockDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao
}
