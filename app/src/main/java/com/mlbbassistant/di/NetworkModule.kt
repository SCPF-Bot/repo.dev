package com.mlbbassistant.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mlbbassistant.data.api.MlbbApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideGson(): Gson = GsonBuilder().serializeNulls().create()

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    /** Builds an [MlbbApiService] for the given URL. Returns null for blank/invalid URLs. */
    fun buildApiService(baseUrl: String, client: OkHttpClient, gson: Gson): MlbbApiService? {
        if (baseUrl.isBlank()) return null
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return try {
            Retrofit.Builder()
                .baseUrl(normalized)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(MlbbApiService::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
