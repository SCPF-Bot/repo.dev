package com.example.mlbbdraftassistant.ui.overlay

import android.app.Application
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mlbbdraftassistant.MLBBDraftAssistantApp
import com.example.mlbbdraftassistant.data.model.Hero
import com.example.mlbbdraftassistant.data.repository.HeroRepositoryImpl
import com.example.mlbbdraftassistant.domain.Recommendation
import com.example.mlbbdraftassistant.domain.RecommendationEngine
import com.example.mlbbdraftassistant.domain.ScoringConfig
import com.example.mlbbdraftassistant.util.DraftDetector
import com.example.mlbbdraftassistant.util.ScreenCaptureManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftState(
    val allies: List<Hero?> = List(5) { null },
    val enemies: List<Hero?> = List(5) { null },
    val availableHeroes: List<Hero> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val isLoading: Boolean = false,
    val isLocked: Boolean = false,
    val isCaptureReady: Boolean = false,
    val detectionError: String? = null
)

class DraftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MLBBDraftAssistantApp).repository
    private val engine = RecommendationEngine(ScoringConfig.DEFAULT)
    val captureManager = ScreenCaptureManager()

    private val _state = MutableStateFlow(DraftState())
    val state: StateFlow<DraftState> = _state.asStateFlow()

    private val metrics: DisplayMetrics by lazy {
        val wm = application.getSystemService(WindowManager::class.java)
        DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
    }

    init {
        viewModelScope.launch {
            (repository as HeroRepositoryImpl).initialize()
            repository.observeHeroes().collect { heroes ->
                _state.update { it.copy(availableHeroes = heroes) }
            }
        }
    }

    fun setAlly(slot: Int, hero: Hero) {
        _state.update { current ->
            val newAllies = current.allies.toMutableList()
            newAllies[slot] = hero
            current.copy(allies = newAllies, detectionError = null)
        }
        recompute()
    }

    fun setEnemy(slot: Int, hero: Hero) {
        _state.update { current ->
            val newEnemies = current.enemies.toMutableList()
            newEnemies[slot] = hero
            current.copy(enemies = newEnemies, detectionError = null)
        }
        recompute()
    }

    fun resetDraft() {
        _state.update {
            it.copy(allies = List(5) { null }, enemies = List(5) { null }, isLocked = false, detectionError = null)
        }
        recompute()
    }

    fun toggleLock() {
        _state.update { it.copy(isLocked = !it.isLocked) }
    }

    fun refreshHeroData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.refreshHeroData()
            _state.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Perform automatic OCR‑based draft detection.
     */
    fun detectDraft() {
        if (!captureManager.isReady()) {
            _state.update { it.copy(detectionError = "Screen capture not ready. Grant permission first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, detectionError = null) }
            try {
                val detector = DraftDetector(captureManager, metrics)
                val result = detector.detect(_state.value.availableHeroes)
                _state.update { current ->
                    current.copy(
                        allies = result.allies,
                        enemies = result.enemies,
                        isLoading = false
                    )
                }
                recompute()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, detectionError = "Detection failed: ${e.message}") }
            }
        }
    }

    private fun recompute() {
        val current = _state.value
        if (current.availableHeroes.isEmpty()) return
        val alliesList = current.allies.filterNotNull()
        val enemiesList = current.enemies.filterNotNull()
        val recs = engine.recommend(
            allies = alliesList,
            enemies = enemiesList,
            availableHeroes = current.availableHeroes
        )
        _state.update { it.copy(recommendations = recs) }
    }
}