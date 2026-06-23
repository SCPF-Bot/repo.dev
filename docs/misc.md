# Miscellaneous Architecture Notes — MLBB Draft Assistant

> This file captures architectural decisions that don't fit cleanly into `overview.md`
> (which is product/system-level) or `findings.md` (which is audit-driven).
> Use it for "small but non-obvious" choices that future maintainers would otherwise
> have to re-discover.

---

## 1. Retry layer: network-interceptor → repository (2026-06-23)

### What changed
`RetryInterceptor` — an OkHttp `Interceptor` that called `Thread.sleep()` between
attempts — was removed from `NetworkModule`. Retry logic now lives in
`HeroRepositoryImpl.syncHeroes()` using coroutine `delay()`.

### Why this deviates from the network-layer-first approach
The "network layer should own retry" convention is widely followed in Android, and
the original intent of `RetryInterceptor` was correct in spirit. However, OkHttp
interceptors run on OkHttp's dispatcher thread pool, which has a bounded size
(default 64 threads). Calling `Thread.sleep()` inside an interceptor occupies a
dispatcher thread for the full back-off window (up to 4 seconds for three retries
with a 500 ms base delay). Under concurrent requests this can starve the pool and
delay unrelated API calls.

The repository-layer approach uses coroutine `delay()` instead, which suspends the
calling coroutine without occupying any thread during the back-off. The retry is
cancellation-aware (cancelled if the lifecycle scope dies), and the retry logic is
testable in isolation without an OkHttp `MockWebServer`.

### Trade-offs accepted
- Retry behaviour is now specific to `syncHeroes()` rather than applying universally to
  all Retrofit calls. Any future endpoint that needs retry must opt in explicitly.
- The network module becomes simpler (no custom interceptors).
- HTTP 4xx/5xx responses are still NOT retried — only `IOException`/timeout failures
  are retried. This was the same policy as the old interceptor.

### When to revisit
If a second network-layer retry need emerges (e.g. another `MetaApi` endpoint that
must auto-retry on timeout), prefer a shared `suspend fun <T> withRetry(...)` utility
function in the repository package over reintroducing an OkHttp interceptor with
`Thread.sleep`.

---

## 2. `@VisibleForTesting` as interim solution for dual scoring entry points (2026-06-23)

`DraftScorer` has two scoring functions:
- `score()` / `rankAll()` — the full production path with adaptive weights, dynamic
  bounds, patch velocity, positional modifiers, and proficiency multipliers.
- `computeScore()` — a simplified three-term linear formula used in unit tests.

The correct long-term fix is to merge these into a single `score()` with a
`simplified = true` parameter. Until then, `computeScore` carries `@VisibleForTesting`
and an expanded KDoc warning so that no future production caller accidentally picks it.
The interim state is tracked in `todo.md §3`.

---

## 3. `PhaseDetectionConfig` as the single source for CV thresholds (TD-03)

All computer-vision thresholds (luminance raw threshold, normalised ratio, frame
throttle intervals, hash distance limits, histogram similarity floor) live in
`capture/PhaseDetectionConfig.kt`. This was TD-03 from the original technical-debt
register.

**Do not define a new threshold in a feature class.** Add it to `PhaseDetectionConfig`
and reference it from the feature class. This makes device-calibration patches a
single-file change.

---

## 4. Notification channel ID ownership

`OverlayService` owns its private `NOTIF_CHANNEL = "overlay_channel"` constant.
`AppConstants` previously held a duplicate (now deleted, P2-02 fix).

**Rule:** each Android component that creates a notification channel owns its own
channel ID constant as a private constant in that class. Do not re-export channel
IDs into `AppConstants` or any cross-module utility — the channel string is an
implementation detail of the component that registers it.

---

## 5. `LUMINANCE_NORMALISED_RATIO` and adaptive brightness (TD-04)

`PhaseDetectionConfig.LUMINANCE_NORMALISED_RATIO` is multiplied by a per-session
luminance baseline sampled from a stable background region (top-left 10 % × 10 % of
the first captured frame). The slot-fill threshold is then:

```
threshold = max(LUMINANCE_DARK_THRESHOLD_RAW, baseline × LUMINANCE_NORMALISED_RATIO)
```

This ensures that if the user has auto-brightness enabled and is playing in a bright
environment, the increased ambient luminance raises both the background and the
"empty slot" luminance together — the normalised threshold rises with them, preventing
false positives. LUMINANCE_DARK_THRESHOLD_RAW is the floor so that in very dark
environments the detector does not collapse to near-zero.

Recalibrate these constants by running the `FrameProcessorBenchmark` instrumented test
against captures from target devices.
