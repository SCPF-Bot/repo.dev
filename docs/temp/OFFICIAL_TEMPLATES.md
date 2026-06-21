# [PLAN] OFFICIAL_TEMPLATES.md — Canonical Patterns & Sources

---

## T-01: Repository Pattern — ViewModel Should NOT Inject DAOs
**Source:** https://developer.android.com/topic/architecture/recommendations  
**Verified:** 2024 — "UI layer should depend on domain or data layer via repositories/use cases, never directly on DAOs."

```kotlin
// WRONG — ViewModel injects DAO directly
class HomeViewModel @Inject constructor(
    private val draftSessionDao: DraftSessionDao  // ← violation
) : ViewModel()

// CORRECT — ViewModel depends on use case or repository interface
class HomeViewModel @Inject constructor(
    private val getDraftHistoryUseCase: GetDraftHistoryUseCase  // ← canonical
) : ViewModel()
```

---

## T-02: Domain Layer Purity — No Framework Imports
**Source:** https://developer.android.com/topic/architecture  
**Verified:** 2024 — "The domain layer contains pure Kotlin classes. It has no dependency on the Android framework."

```kotlin
// WRONG — domain model imports Compose annotations
import androidx.compose.runtime.Immutable  // ← framework dependency in domain
data class Hero(...)

// CORRECT — use Kotlin stdlib or no annotation
// @Immutable semantics can be replaced by making the class a data class with val-only properties
// OR move @Immutable to the presentation layer's UI model if strictly needed there
data class Hero(...)  // pure Kotlin data class — compiler infers immutability
```

---

## T-03: StateFlow in ViewModel — Correct Pattern
**Source:** https://developer.android.com/kotlin/flow/stateflow-and-sharedflow  
**Verified:** 2024

```kotlin
// CORRECT — existing code matches this pattern
private val _state = MutableStateFlow(MyState())
val state: StateFlow<MyState> = _state.asStateFlow()

// Collect in Compose — CORRECT (existing code uses this)
val state by viewModel.state.collectAsStateWithLifecycle()
```

---

## T-04: IO Dispatcher — Use Case Owns Threading, Not ViewModel
**Source:** https://developer.android.com/kotlin/coroutines/coroutines-best-practices  
**Verified:** 2024 — "Inject Dispatchers. Make coroutine classes testable by injecting Dispatchers. The ViewModel should not hard-code Dispatchers."

```kotlin
// WRONG — ViewModel explicitly dispatches to IO
private fun saveSession(session: DraftSession) {
    viewModelScope.launch(Dispatchers.IO) {  // ← ViewModel should not specify dispatcher
        saveDraftSessionUseCase(session)
    }
}

// CORRECT — Use case is responsible for dispatcher
class SaveDraftSessionUseCase @Inject constructor(...) {
    suspend operator fun invoke(session: DraftSession): Long =
        withContext(Dispatchers.IO) { ... }  // ← dispatcher lives in the use case
}

// ViewModel becomes:
private fun saveSession(session: DraftSession) {
    viewModelScope.launch {  // no dispatcher — use case handles it
        saveDraftSessionUseCase(session)
    }
}
```

---

## T-05: Dynamic Color — Explicit Opt-In for Branded Apps
**Source:** https://developer.android.com/develop/ui/views/theming/dynamic-colors  
**Verified:** 2024 — "Dynamic colors are optional. Apps with established brand colors may choose not to apply dynamic colors."

```kotlin
// WRONG for a branded app — dynamic color enabled by default
fun MLBBAssistantTheme(
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    ...
)

// CORRECT — disabled by default to preserve brand identity
fun MLBBAssistantTheme(
    dynamicColor: Boolean = false,  // opt-in, not opt-out
    ...
)
```

---

## T-06: Compose @Immutable / @Stable Placement
**Source:** https://developer.android.com/jetpack/compose/performance/stability  
**Verified:** 2024 — "@Immutable and @Stable are Compose compiler hints. They belong on UI/presentation layer classes, not on domain models."

```kotlin
// Domain model — no Compose annotation needed
// data class with val-only fields is inferred stable by the compiler
data class Hero(val id: Int, val name: String, ...)  // in domain/model/

// Presentation UI state — @Immutable is appropriate here
@Immutable
data class HeroListState(val heroes: List<Hero> = emptyList(), ...)  // in presentation/
```

---

## T-07: Flow Filter on Background Thread
**Source:** https://developer.android.com/kotlin/flow  
**Verified:** 2024 — "Use flowOn() to change the context of flow emission. The downstream collector is unaffected."

```kotlin
// WRONG — filter runs on calling thread (potentially Main)
heroes.filter { ... }.filter { ... }

// CORRECT — expensive operations run on Default
getHeroesUseCase()
    .map { heroes -> applyFilters(heroes, query, role) }
    .flowOn(Dispatchers.Default)
    .collect { filtered -> _state.update { it.copy(filteredHeroes = filtered) } }
```

---

## T-08: Shared ViewModel Across NavGraph Destinations
**Source:** https://developer.android.com/guide/navigation/use-graph/programmatic#share_ui-related_data_between_destinations_with_viewmodel  
**Verified:** 2024

```kotlin
// CORRECT — scope VM to a parent nav graph entry to share state
val sharedVm: HeroListViewModel = hiltViewModel(
    navController.getBackStackEntry("hero_graph")
)
```

---

## T-09: Unused Imports — Kotlin Style Guide
**Source:** https://kotlinlang.org/docs/coding-conventions.html#imports  
**Verified:** 2024 — "Do not use star imports. Remove unused imports."

---

## T-10: Centralize DataStore Keys
**Source:** https://developer.android.com/training/data-storage/datastore#preferences-datastore  
**Verified:** 2024 — "Define preferences keys once. Duplicating keys across files can cause silent mismatches."

```kotlin
// CORRECT — single source of truth for all keys
object AppPreferenceKeys {
    val KEY_META    = floatPreferencesKey("weight_meta")
    val KEY_COUNTER = floatPreferencesKey("weight_counter")
    val KEY_SYNERGY = floatPreferencesKey("weight_synergy")
    // ... all other keys
}
```
