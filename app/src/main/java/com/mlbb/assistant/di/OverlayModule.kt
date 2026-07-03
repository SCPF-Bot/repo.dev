package com.mlbb.assistant.di

import android.content.Context
import android.provider.Settings
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.mlbb.assistant.domain.OverlayController
import com.mlbb.assistant.presentation.overlay.OverlayService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OverlayModule {

    /**
     * Provides the domain-layer [OverlayController] implementation backed by
     * [OverlayService]. Permission check is performed here so callers don't
     * need to know about [Settings.canDrawOverlays].
     */
    @Provides
    @Singleton
    fun provideOverlayController(
        @ApplicationContext context: Context
    ): OverlayController = object : OverlayController {
        override fun start() {
            if (Settings.canDrawOverlays(context)) OverlayService.start(context)
        }
        override fun stop() {
            OverlayService.stop(context)
        }
    }

    /**
     * Provides a singleton [ImageLoader] for portrait downloads and hash preloading.
     *
     * Uses the app's shared [OkHttpClient] (30 s timeouts) with an added User-Agent
     * header so the MLBB CDN (`akmweb.youngjoygame.com`) accepts the requests.
     * Without a recognisable User-Agent the CDN returns non-image responses that
     * Coil cannot decode, causing every portrait download to fail silently.
     *
     * The [OkHttpNetworkFetcherFactory] wires Coil 3 to the OkHttp engine from the
     * `coil-network-okhttp` artifact that is already on the classpath.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageLoader {
        val portraitClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36")
                        .build()
                )
            }
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { portraitClient })) }
            .build()
    }
}
