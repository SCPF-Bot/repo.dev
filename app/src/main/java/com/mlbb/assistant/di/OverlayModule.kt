package com.mlbb.assistant.di

import android.content.Context
import android.provider.Settings
import coil3.ImageLoader
import com.mlbb.assistant.domain.OverlayController
import com.mlbb.assistant.presentation.overlay.OverlayService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
     * Provides a singleton [ImageLoader] for portrait hash preloading in
     * [com.mlbb.assistant.presentation.overlay.OverlayCaptureCoordinator].
     *
     * Coil's default [ImageLoader] is fine here — it is disk-cached and
     * lifecycle-aware. Injected rather than constructed inline in the service
     * so tests can substitute a no-op loader.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader = ImageLoader.Builder(context).build()
}
