package com.adwatch.app.di

import com.adwatch.feature.ads.AdsApiService
import com.adwatch.feature.cashout.CashoutApiService
import com.adwatch.feature.home.HomeApiService
import com.adwatch.feature.wallet.WalletApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiServiceModule {

    @Provides
    @Singleton
    fun provideAdsApiService(retrofit: Retrofit): AdsApiService {
        return retrofit.create(AdsApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideHomeApiService(retrofit: Retrofit): HomeApiService {
        return retrofit.create(HomeApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideWalletApiService(retrofit: Retrofit): WalletApiService {
        return retrofit.create(WalletApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideCashoutApiService(retrofit: Retrofit): CashoutApiService {
        return retrofit.create(CashoutApiService::class.java)
    }
}
