package com.tangtang.stockadvisor.data.api

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiClient(): ApiClient = ApiClient()

    @Provides
    @Singleton
    fun provideStockApiService(apiClient: ApiClient): StockApiService = apiClient.apiService
}
