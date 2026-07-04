package com.mlbb.assistant.data.export

import android.content.ContentResolver
import android.os.Build
import android.provider.MediaStore
import com.mlbb.assistant.data.local.database.DraftSessionEntity
import com.mlbb.assistant.domain.model.DraftOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * todo.md §4 — Round-trip serialisation test for [DraftExporter].
 *
 * Uses `sdk = [29]` (Android Q) so [MediaStore.Downloads] / [MediaStore.Images]
 * — the API-29+ paths [DraftExporter] takes on modern devices — are exercised
 * by Robolectric's shadow ContentResolver, rather than falling through to the
 * legacy `Environment.getExternalStoragePublicDirectory` branch that only
 * applies below API 29.
 *
 * [DraftExporter] is constructed directly (no Hilt) with the Robolectric
 * application context ([RuntimeEnvironment.getApplication], part of Robolectric
 * core — no extra `androidx.test:core` dependency needed), mirroring how other
 * non-Hilt unit tests in this module avoid pulling in the DI graph for a
 * single-class test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [29])
class DraftExporterTest {

    private val context = RuntimeEnvironment.getApplication()
    private val exporter = DraftExporter(context)

    private fun sampleSession(id: Int = 1) = DraftSessionEntity(
        id = id,
        timestamp = 1_700_000_000_000L,
        rank = "Mythic",
        banTotal = 6,
        enemyBanIds = listOf(10, 11, -1),
        yourBanIds = listOf(20, 21, -1),
        enemyPickIds = listOf(1, 2, 3, 4, 5),
        yourPickIds = listOf(6, 7, 8, 9, 10),
        ourTeamFirst = true,
        draftScore = 78,
        metaScore = 65,
        counterScore = 80,
        synergyScore = 70,
        followedRecommendations = 3,
        totalRecommendations = 5,
        outcome = DraftOutcome.WIN.name,
        isSimulation = false
    )

    @Test
    fun exportDraftImage_succeedsAndReturnsReadableUri() = runTest {
        val result = exporter.exportDraftImage(sampleSession())

        assertTrue("exportDraftImage should succeed on API 29+: ${result.exceptionOrNull()}", result.isSuccess)
        val uriString = result.getOrThrow()
        assertTrue(uriString.startsWith("content://"))

        val resolver: ContentResolver = context.contentResolver
        val uri = android.net.Uri.parse(uriString)
        resolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            // PNG magic number — confirms a real, non-empty image was written.
            assertTrue("expected non-trivial PNG payload", bytes.size > 100)
            assertEquals(0x89.toByte(), bytes[0])
            assertEquals('P'.code.toByte(), bytes[1])
            assertEquals('N'.code.toByte(), bytes[2])
            assertEquals('G'.code.toByte(), bytes[3])
        } ?: throw AssertionError("openInputStream returned null for exported image URI")
    }

    @Test
    fun exportHistoryCsv_roundTripsHeaderAndRows() = runTest {
        val sessions = listOf(sampleSession(1), sampleSession(2))
        val result = exporter.exportHistoryCsv(sessions)

        assertTrue("exportHistoryCsv should succeed on API 29+: ${result.exceptionOrNull()}", result.isSuccess)
        val uriString = result.getOrThrow()
        assertTrue(uriString.startsWith("content://"))

        val resolver: ContentResolver = context.contentResolver
        val uri = android.net.Uri.parse(uriString)
        val lines = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }
            ?: throw AssertionError("openInputStream returned null for exported CSV URI")

        // Header + 2 data rows.
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("id,timestamp,rank"))
        assertTrue(lines[1].contains("Mythic"))
        assertTrue(lines[1].contains("WIN"))
        assertTrue(lines[1].contains("\"6;7;8;9;10\""))
    }

    @Test
    fun exportHistoryCsv_withEmptyList_stillWritesHeaderOnly() = runTest {
        val result = exporter.exportHistoryCsv(emptyList())
        assertTrue(result.isSuccess)

        val resolver: ContentResolver = context.contentResolver
        val uri = android.net.Uri.parse(result.getOrThrow())
        val lines = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }
            ?: throw AssertionError("openInputStream returned null")

        assertEquals(1, lines.size)
    }

    init {
        // Sanity check: this test is only meaningful on the Q+ (API 29) MediaStore path.
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "DraftExporterTest requires @Config(sdk = [29]) or higher"
        }
    }
}
