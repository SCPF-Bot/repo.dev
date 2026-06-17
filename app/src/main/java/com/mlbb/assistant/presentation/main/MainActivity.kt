package com.mlbb.assistant.presentation.main

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mlbb.assistant.data.local.database.DraftSessionDao
import com.mlbb.assistant.data.local.database.DraftSessionEntity
import com.mlbb.assistant.domain.model.Hero
import com.mlbb.assistant.domain.repository.HeroRepository
import com.mlbb.assistant.presentation.common.theme.MLBBAssistantTheme
import com.mlbb.assistant.presentation.common.theme.SurfaceDark
import com.mlbb.assistant.presentation.herodetail.HeroDetailScreen
import com.mlbb.assistant.presentation.herolist.HeroListScreen
import com.mlbb.assistant.presentation.history.DraftHistoryScreen
import com.mlbb.assistant.presentation.home.HomeScreen
import com.mlbb.assistant.presentation.metaboard.MetaBoardScreen
import com.mlbb.assistant.presentation.overlay.OverlayService
import com.mlbb.assistant.presentation.settings.SettingsScreen
import com.mlbb.assistant.presentation.welcome.PermissionWizardScreen
import com.mlbb.assistant.service.ScreenCaptureManager
import com.mlbb.assistant.service.VoiceAlertService  // Pass 3: inject to call shutdown() on destroy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

enum class AppScreen {
    WIZARD, HOME, HERO_LIST, HERO_DETAIL, META_BOARD, HISTORY, SETTINGS
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var heroRepository: HeroRepository
    @Inject lateinit var draftSessionDao: DraftSessionDao
    // Pass 3: VoiceAlertService holds a TextToSpeech instance; shutdown() must be called
    // to release the TTS engine when the Activity is destroyed.
    @Inject lateinit var voiceAlertService: VoiceAlertService

    private lateinit var screenCaptureManager: ScreenCaptureManager
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            screenCaptureManager.startCapture(result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenCaptureManager = ScreenCaptureManager(this)

        setContent {
            MLBBAssistantTheme {
                AppNavHost(
                    heroRepository       = heroRepository,
                    draftSessionDao      = draftSessionDao,
                    onStartOverlay       = { startOverlay() },
                    onRequestCapture     = { requestScreenCapture() }
                )
            }
        }
    }

    override fun onDestroy() {
        screenCaptureManager.stopCapture()
        voiceAlertService.shutdown()  // Pass 3: release TTS engine before super — prevents resource leak
        super.onDestroy()
    }

    private fun startOverlay() {
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
    }

    private fun requestScreenCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}

@Composable
private fun AppNavHost(
    heroRepository: HeroRepository,
    draftSessionDao: DraftSessionDao,
    onStartOverlay: () -> Unit,
    onRequestCapture: () -> Unit
) {
    // rememberSaveable survives configuration changes AND process death
    // (AppScreen is an enum → Serializable; Int? is Serializable)
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var selectedHeroId by rememberSaveable { mutableStateOf<Int?>(null) }

    val heroesState = produceState(initialValue = emptyList<Hero>()) {
        // getHeroes() now returns Flow<List<Hero>> — no .toDomain() mapping needed
        heroRepository.getHeroes().collectLatest { value = it }
    }
    val heroes = heroesState.value
    val heroMap = remember(heroes) { heroes.associateBy { it.id } }

    val sessionsState = produceState(initialValue = emptyList<DraftSessionEntity>()) {
        draftSessionDao.getRecentSessions().collectLatest { value = it }
    }

    Box(Modifier.fillMaxSize().background(SurfaceDark)) {
        when (currentScreen) {
            AppScreen.WIZARD     -> PermissionWizardScreen(onComplete = { currentScreen = AppScreen.HOME })
            AppScreen.HOME       -> HomeScreen(
                onStartDraft   = { onStartOverlay(); currentScreen = AppScreen.HOME },
                onOpenExplorer = { currentScreen = AppScreen.HERO_LIST },
                onOpenMeta     = { currentScreen = AppScreen.META_BOARD },
                onOpenHistory  = { currentScreen = AppScreen.HISTORY },
                onOpenSettings = { currentScreen = AppScreen.SETTINGS }
            )
            AppScreen.HERO_LIST  -> HeroListScreen(onBack = { currentScreen = AppScreen.HOME })
            AppScreen.HERO_DETAIL -> {
                val hero = heroMap[selectedHeroId]
                if (hero != null) {
                    HeroDetailScreen(
                        hero          = hero,
                        relatedHeroes = heroMap,
                        onBack        = { currentScreen = AppScreen.HERO_LIST }
                    )
                } else { currentScreen = AppScreen.HERO_LIST }
            }
            AppScreen.META_BOARD -> MetaBoardScreen(
                heroes       = heroes,
                onHeroClick  = { h -> selectedHeroId = h.id; currentScreen = AppScreen.HERO_DETAIL },
                onBack       = { currentScreen = AppScreen.HOME }
            )
            AppScreen.HISTORY    -> DraftHistoryScreen(
                sessions = sessionsState.value,
                onBack   = { currentScreen = AppScreen.HOME }
            )
            AppScreen.SETTINGS   -> SettingsScreen(onBack = { currentScreen = AppScreen.HOME })
        }
    }
}
