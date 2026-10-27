package com.fitlife.app.di

import android.content.Context
import androidx.room.Room
import com.fitlife.app.data.local.FitLifeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): FitLifeDatabase =
        Room.databaseBuilder(ctx, FitLifeDatabase::class.java, "fitlife_v1.db")
            .addMigrations(FitLifeDatabase.MIGRATION_1_2)
            // Only used if a migration path is ever missing; real migrations above keep user data.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideStepsDao(db: FitLifeDatabase)     = db.stepsDao()
    @Provides fun provideWorkoutDao(db: FitLifeDatabase)   = db.workoutDao()
    @Provides fun provideFoodLogDao(db: FitLifeDatabase)   = db.foodLogDao()
    @Provides fun provideDietPlanDao(db: FitLifeDatabase)  = db.dietPlanDao()
    @Provides fun provideSleepDao(db: FitLifeDatabase)     = db.sleepDao()
    @Provides fun provideWellbeingDao(db: FitLifeDatabase) = db.wellbeingDao()
    @Provides fun provideProfileDao(db: FitLifeDatabase)   = db.profileDao()
    @Provides fun provideRideDao(db: FitLifeDatabase)      = db.rideDao()

    // FoodRepository is NOT listed here — it uses @Inject constructor() directly
}
