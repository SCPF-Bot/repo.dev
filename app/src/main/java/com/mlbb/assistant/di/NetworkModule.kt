package com.mlbb.assistant.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mlbb.assistant.BuildConfig
import com.mlbb.assistant.data.remote.api.MetaApi
import com.pluto.plugins.network.interceptors.okhttp.PlutoOkhttpInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * P1-02 fix: [RetryInterceptor] has been removed from this module.
 *
 * The previous implementation used [Thread.sleep] inside the OkHttp interceptor
 * to implement exponential back-off. Blocking an OkHttp dispatcher thread for up
 * to 4 seconds under concurrent network calls can starve the thread pool (max 64
 * threads by default) and delay unrelated requests.
 *
 * Retry logic has been moved to [com.mlbb.assistant.data.repository.HeroRepositoryImpl]
 * where it uses coroutine [kotlinx.coroutines.delay] — non-blocking, cancellation-
 * aware, and properly integrated with structured concurrency.
 *
 * HTTP 4xx/5xx responses are NOT retried (same policy as before) and must be
 * handled by callers via the [com.mlbb.assistant.utils.NetworkResult] sealed class.
 *
 * P3-01: Migrated from GsonConverterFactory to kotlinx.serialization converter.
 * [ignoreUnknownKeys] = true so new server fields never crash older app versions.
 * [coerceInputValues] = true so null → default for non-nullable primitives.
 * Gson dependency kept in Gradle until minified-build smoke test confirms R8 rules
 * are clean (todo.md §5.5 step 5).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient         = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }
        // PlutoOkhttpInterceptor captures every request/response for the Pluto
        // network inspector panel. The release no-op companion is a pass-through.
        // Must be added LAST so it sees the fully-decorated request.
        builder.addInterceptor(PlutoOkhttpInterceptor)
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.META_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideMetaApi(retrofit: Retrofit): MetaApi = retrofit.create(MetaApi::class.java)
}
