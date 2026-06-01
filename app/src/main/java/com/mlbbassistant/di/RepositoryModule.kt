package com.mlbbassistant.di

import com.mlbbassistant.data.repository.HeroRepository
import com.mlbbassistant.data.repository.HeroRepositoryImpl
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
    abstract fun bindHeroRepository(impl: HeroRepositoryImpl): HeroRepository
}
