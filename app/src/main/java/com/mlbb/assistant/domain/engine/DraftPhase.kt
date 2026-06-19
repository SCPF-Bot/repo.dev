package com.mlbb.assistant.domain.engine

/**
 * Discrete phases of an MLBB draft session.
 *
 * Phase transitions are driven by [capture.PhaseDetector] reading screen frames
 * in real time, and managed by [DraftSessionManager].
 */
enum class DraftPhase {
    /** No draft is in progress. Overlay shows a minimal "Start" button. */
    IDLE,

    /** Pre-ban countdown visible. Overlay shows team composition tips. */
    SETUP,

    /** First ban round (typically 3 bans per team). */
    BAN_ROUND_1,

    /** Second ban round (typically 2 bans per team in ranked). */
    BAN_ROUND_2,

    /** Hero pick phase. Overlay shows ranked pick suggestions. */
    PICK,

    /** Post-pick trading window. Overlay shows swap recommendations. */
    TRADING,

    /** Draft is complete. Session is persisted and final score is shown. */
    COMPLETE
}
