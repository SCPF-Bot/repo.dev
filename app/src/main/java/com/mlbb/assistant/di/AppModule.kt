package com.mlbb.assistant.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.mlbb.assistant.service.VoiceAlertService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mlbb_preferences")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides @Singleton
    fun provideDraftSessionManager(): DraftSessionManager = DraftSessionManager()

    @Provides @Singleton
    fun provideVoiceAlertService(@ApplicationContext context: Context): VoiceAlertService =
        VoiceAlertService(context)
}
