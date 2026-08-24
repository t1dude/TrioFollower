package com.trionsandroid.app.di

import com.trionsandroid.app.data.nightscout.NightscoutRepository
import com.trionsandroid.app.data.nightscout.NightscoutRepositoryImpl
import com.trionsandroid.app.data.settings.SettingsRepository
import com.trionsandroid.app.data.settings.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindNightscoutRepository(impl: NightscoutRepositoryImpl): NightscoutRepository
}
