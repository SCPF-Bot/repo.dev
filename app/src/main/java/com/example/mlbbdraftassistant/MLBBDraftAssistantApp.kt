package com.example.mlbbdraftassistant

import android.app.Application
import com.example.mlbbdraftassistant.data.repository.HeroRepository
import com.example.mlbbdraftassistant.data.repository.HeroRepositoryImpl

class MLBBDraftAssistantApp : Application() {

    lateinit var repository: HeroRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = HeroRepositoryImpl(this)
    }
}