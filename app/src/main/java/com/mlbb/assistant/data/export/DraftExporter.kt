package com.mlbb.assistant.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.mlbb.assistant.data.local.database.DraftSessionEntity
import com.mlbb.assistant.domain.model.DraftOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Section 6.3 — Draft export utilities.
 *
 * 6.3.1 [exportDraftImage]: Renders a [DraftSessionEntity] to a PNG card
 *        (800×600 px) and saves it to the device's Pictures/MLBB Assistant
 *        folder via [MediaStore] (API 29+) or a public Pictures fallback.
 *
 * 6.3.2 [exportHistoryCsv]: Serialises a list of [DraftSessionEntity]
 *        rows to a CSV file in Downloads/MLBB Assistant.
 *
 * Both methods run on [Dispatchers.IO] and return a [Result] so callers
 * can surface success/failure without crashing on I/O errors.
 */
@Singleton
class DraftExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val DIR_NAME     = "MLBB Assistant"
        private const val IMG_W        = 800
        private const val IMG_H        = 480
        private const val MIME_PNG     = "image/png"
        private const val MIME_CSV     = "text/csv"

        private val CSV_HEADER = listOf(
            "id", "timestamp", "rank", "our_team_first", "is_simulation",
            "outcome", "draft_score", "meta_score", "counter_score", "synergy_score",
            "followed_recommendations", "total_recommendations",
            "our_picks", "enemy_picks", "our_bans", "enemy_bans"
        ).joinToString(",")
    }

    // ── 6.3.1 Image export ────────────────────────────────────────────────────

    /**
     * Renders [session] as a summary card PNG and saves it to the device.
     *
     * @return [Result.success] with the file URI string on success.
     *         [Result.failure] with the underlying exception on failure.
     */
    suspend fun exportDraftImage(session: DraftSessionEntity): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bmp = renderCard(session)
                val filename = "draft_${session.id}_${System.currentTimeMillis()}.png"
                saveBitmapToGallery(bmp, filename).also { bmp.recycle() }
            }
        }

    private fun renderCard(s: DraftSessionEntity): Bitmap {
        val bmp = Bitmap.createBitmap(IMG_W, IMG_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Background
        canvas.drawColor(Color.parseColor("#1A1A2E"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Gold top bar
        paint.color = Color.parseColor("#F5A623")
        canvas.drawRect(0f, 0f, IMG_W.toFloat(), 6f, paint)

        // Title
        paint.color = Color.parseColor("#F5A623")
        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("MLBB Draft Summary", 24f, 50f, paint)

        // Date + rank
        val fmt = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
        paint.color = Color.parseColor("#A0A0B0")
        paint.textSize = 18f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("${fmt.format(Date(s.timestamp))}", 24f, 80f, paint)
        canvas.drawText("Rank: ${s.rank}   ${if (s.ourTeamFirst) "First pick" else "Second pick"}", 24f, 105f, paint)

        // Outcome badge
        val outcomeText = when (DraftOutcome.fromString(s.outcome)) {
            DraftOutcome.WIN     -> "WIN"
            DraftOutcome.LOSS    -> "LOSS"
            DraftOutcome.UNKNOWN -> "—"
        }
        val outcomeColor = when (DraftOutcome.fromString(s.outcome)) {
            DraftOutcome.WIN     -> Color.parseColor("#10B981")
            DraftOutcome.LOSS    -> Color.parseColor("#EF4444")
            DraftOutcome.UNKNOWN -> Color.parseColor("#6B7280")
        }
        paint.color = outcomeColor
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(outcomeText, IMG_W - 80f, 60f, paint)

        // Divider
        paint.color = Color.parseColor("#2A2A4E")
        canvas.drawRect(24f, 120f, IMG_W - 24f, 122f, paint)

        // Score stats
        val scores = listOf(
            "Draft" to s.draftScore,
            "Meta"  to s.metaScore,
            "Counter" to s.counterScore,
            "Synergy" to s.synergyScore
        )
        scores.forEachIndexed { i, (label, value) ->
            val x = 24f + i * 190f
            paint.color = Color.parseColor("#F5A623")
            paint.textSize = 36f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("$value", x, 175f, paint)
            paint.color = Color.parseColor("#A0A0B0")
            paint.textSize = 14f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(label, x, 195f, paint)
        }

        // Recommendation rate
        val rate = if (s.totalRecommendations > 0)
            s.followedRecommendations * 100 / s.totalRecommendations else 0
        paint.color = Color.parseColor("#A0A0B0")
        paint.textSize = 15f
        canvas.drawText("Followed recommendations: $rate%", 24f, 230f, paint)

        // Our picks
        paint.color = Color.parseColor("#06B6D4")
        paint.textSize = 16f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("OUR PICKS", 24f, 270f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.WHITE
        val ourPickStr = s.yourPickIds.filter { it >= 0 }.joinToString(", ") { "#$it" }
        canvas.drawText(if (ourPickStr.isNotEmpty()) ourPickStr else "—", 24f, 295f, paint)

        // Enemy picks
        paint.color = Color.parseColor("#EF4444")
        paint.textSize = 16f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("ENEMY PICKS", 24f, 335f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.WHITE
        val enemyPickStr = s.enemyPickIds.filter { it >= 0 }.joinToString(", ") { "#$it" }
        canvas.drawText(if (enemyPickStr.isNotEmpty()) enemyPickStr else "—", 24f, 360f, paint)

        // Footer
        paint.color = Color.parseColor("#F5A623")
        canvas.drawRect(0f, IMG_H - 4f, IMG_W.toFloat(), IMG_H.toFloat(), paint)
        paint.color = Color.parseColor("#505070")
        paint.textSize = 12f
        canvas.drawText("MLBB Draft Assistant", 24f, IMG_H - 12f, paint)

        return bmp
    }

    private fun saveBitmapToGallery(bmp: Bitmap, filename: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DIR_NAME")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                ?: error("MediaStore insert returned null")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            uri.toString()
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DIR_NAME)
            dir.mkdirs()
            val file = File(dir, filename)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file.absolutePath
        }
    }

    // ── 6.3.2 CSV export ──────────────────────────────────────────────────────

    /**
     * Exports [sessions] to a CSV file in the Downloads directory.
     *
     * @return [Result.success] with the file path on success.
     *         [Result.failure] with the underlying exception on failure.
     */
    suspend fun exportHistoryCsv(sessions: List<DraftSessionEntity>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val filename = "mlbb_draft_history_${System.currentTimeMillis()}.csv"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, filename)
                        put(MediaStore.Downloads.MIME_TYPE, MIME_CSV)
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DIR_NAME")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                        ?: error("MediaStore Downloads insert returned null")
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                        w.write(CSV_HEADER)
                        w.newLine()
                        sessions.forEach { s ->
                            w.write(toCsvRow(s))
                            w.newLine()
                        }
                    }
                    uri.toString()
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME)
                    dir.mkdirs()
                    val file = File(dir, filename)
                    FileWriter(file).buffered().use { w ->
                        w.write(CSV_HEADER)
                        w.newLine()
                        sessions.forEach { s ->
                            w.write(toCsvRow(s))
                            w.newLine()
                        }
                    }
                    file.absolutePath
                }
            }
        }

    private fun toCsvRow(s: DraftSessionEntity): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return listOf(
            s.id.toString(),
            fmt.format(Date(s.timestamp)),
            s.rank,
            s.ourTeamFirst.toString(),
            s.isSimulation.toString(),
            s.outcome,
            s.draftScore.toString(),
            s.metaScore.toString(),
            s.counterScore.toString(),
            s.synergyScore.toString(),
            s.followedRecommendations.toString(),
            s.totalRecommendations.toString(),
            "\"${s.yourPickIds.joinToString(";")}\"",
            "\"${s.enemyPickIds.joinToString(";")}\"",
            "\"${s.yourBanIds.joinToString(";")}\"",
            "\"${s.enemyBanIds.joinToString(";")}\"",
        ).joinToString(",")
    }
}
