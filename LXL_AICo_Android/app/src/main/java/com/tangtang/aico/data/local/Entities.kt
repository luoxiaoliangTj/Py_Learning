package com.tangtang.aico.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_stocks")
data class CachedStock(
    @PrimaryKey val code: String,
    val name: String,
    val currentPrice: Double,
    val changePercent: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watchlist")
data class WatchlistItem(
    @PrimaryKey val code: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "portfolio_cache")
data class PortfolioCache(
    @PrimaryKey val code: String,
    val name: String,
    val shares: Int,
    val avgCost: Double,
    val currentPrice: Double,
    val updatedAt: Long = System.currentTimeMillis()
)
