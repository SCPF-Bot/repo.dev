package com.mlbb.assistant.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.mlbb.assistant.appDataStore
import com.mlbb.assistant.domain.engine.DraftSessionManager
import com.mlbb.assistant.service.VoiceAlertService
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
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.appDataStore

    @Provides @Singleton
    fun provideDraftSessionManager(): DraftSessionManager = DraftSessionManager()

    @Provides @Singleton
    fun provideVoiceAlertService(@ApplicationContext context: Context): VoiceAlertService =
        VoiceAlertService(context)
}
