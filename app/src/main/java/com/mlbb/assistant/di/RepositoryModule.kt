package com.mlbb.assistant.di

import com.mlbb.assistant.data.repository.DraftSessionRepositoryImpl
import com.mlbb.assistant.data.repository.HeroRepositoryImpl
import com.mlbb.assistant.domain.repository.DraftSessionRepository
import com.mlbb.assistant.domain.repository.HeroRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindHeroRepository(impl: HeroRepositoryImpl): HeroRepository

    @Binds @Singleton
    abstract fun bindDraftSessionRepository(impl: DraftSessionRepositoryImpl): DraftSessionRepository
}
