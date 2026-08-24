package com.trionsandroid.app.di

import android.content.Context
import androidx.room.Room
import com.trionsandroid.app.data.local.GlucoseEntryDao
import com.trionsandroid.app.data.local.TreatmentDao
import com.trionsandroid.app.data.local.TrioDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTrioDatabase(@ApplicationContext context: Context): TrioDatabase =
        Room.databaseBuilder(context, TrioDatabase::class.java, "trio.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideGlucoseEntryDao(database: TrioDatabase): GlucoseEntryDao = database.glucoseEntryDao()

    @Provides
    fun provideTreatmentDao(database: TrioDatabase): TreatmentDao = database.treatmentDao()
}
