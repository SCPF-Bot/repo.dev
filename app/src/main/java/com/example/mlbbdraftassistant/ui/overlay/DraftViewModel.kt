package com.example.mlbbdraftassistant.ui.overlay

import android.app.Application
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.example.mlbbdraftassistant.MLBBDraftAssistantApp
import com.example.mlbbdraftassistant.data.model.Hero
import com.example.mlbbdraftassistant.data.repository.HeroRepositoryImpl
import com.example.mlbbdraftassistant.domain.Recommendation
import com.example.mlbbdraftassistant.domain.RecommendationEngine
import com.example.mlbbdraftassistant.domain.ScoringConfig
import com.example.mlbbdraftassistant.util.DraftDetector
import com.example.mlbbdraftassistant.util.IconDetector
import com.example.mlbbdraftassistant.util.PrefKeys
import com.example.mlbbdraftassistant.util.ScreenCaptureManager
import kotlinx.coroutines.Job
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
    val detectionError: String? = null,
    val detectionMode: DetectionMode = DetectionMode.OCR
)

enum class DetectionMode { OCR, ICON, MANUAL }

class DraftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MLBBDraftAssistantApp).repository
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    // Build scoring config from preferences
    private var scoringConfig = ScoringConfig(
        synergyWeight = prefs.getFloat(PrefKeys.WEIGHT_SYNERGY, 0.30f),
        counterWeight = prefs.getFloat(PrefKeys.WEIGHT_COUNTER, 0.40f),
        roleWeight = prefs.getFloat(PrefKeys.WEIGHT_ROLE, 0.10f),
        metaWeight = prefs.getFloat(PrefKeys.WEIGHT_META, 0.20f)
    )
    private var engine = RecommendationEngine(scoringConfig)

    val captureManager = ScreenCaptureManager()

    private val _state = MutableStateFlow(
        DraftState(
            detectionMode = when (prefs.getString(PrefKeys.DETECTION_MODE, "ocr")) {
                "icon" -> DetectionMode.ICON
                "manual" -> DetectionMode.MANUAL
                else -> DetectionMode.OCR
            }
        )
    )
    val state: StateFlow<DraftState> = _state.asStateFlow()

    private val metrics: DisplayMetrics by lazy {
        val wm = application.getSystemService(WindowManager::class.java)
        DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
    }

    private var detectJob: Job? = null

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

    fun setDetectionMode(mode: DetectionMode) {
        _state.update { it.copy(detectionMode = mode) }
    }

    fun refreshHeroData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.refreshHeroData()
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun detectDraft() {
        val currentMode = _state.value.detectionMode
        if (currentMode == DetectionMode.MANUAL) {
            _state.update { it.copy(detectionError = "Manual mode — no automatic detection.") }
            return
        }
        if (!captureManager.isReady()) {
            _state.update { it.copy(detectionError = "Screen capture not ready. Grant permission first.") }
            return
        }
        if (detectJob?.isActive == true) {
            return
        }
        detectJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, detectionError = null) }
            try {
                val result = when (currentMode) {
                    DetectionMode.OCR -> {
                        DraftDetector(captureManager, metrics).detect(_state.value.availableHeroes)
                    }
                    DetectionMode.ICON -> {
                        val context = getApplication<MLBBDraftAssistantApp>()
                        IconDetector(context, captureManager, metrics).detect(_state.value.availableHeroes)
                    }
                    DetectionMode.MANUAL -> {
                        null // unreachable
                    }
                }
                if (result != null) {
                    _state.update { current ->
                        current.copy(
                            allies = result.allies,
                            enemies = result.enemies,
                            isLoading = false
                        )
                    }
                    recompute()
                }
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

    override fun onCleared() {
        super.onCleared()
        detectJob?.cancel()
        captureManager.release()
    }
}