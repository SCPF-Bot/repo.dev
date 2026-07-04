package com.mlbb.assistant.domain.usecase

import com.mlbb.assistant.domain.OverlayController
import javax.inject.Inject

/**
 * Starts or stops the overlay.
 *
 * Domain-pure: no android.* imports, no reference to the presentation layer.
 * The platform check (canDrawOverlays) and service binding are handled by
 * the [OverlayController] implementation injected from [di.OverlayModule].
 */
class ToggleOverlayUseCase @Inject constructor(
    private val overlayController: OverlayController
) {
    operator fun invoke(start: Boolean) {
        if (start) overlayController.start() else overlayController.stop()
    }
}
