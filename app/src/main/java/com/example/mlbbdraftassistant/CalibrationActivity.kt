package com.example.mlbbdraftassistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.mlbbdraftassistant.util.CropRegion
import com.example.mlbbdraftassistant.util.CropRegions

class CalibrationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved regions (or defaults) into memory
        CropRegions.loadFromPrefs(this)

        setContent {
            MaterialTheme {
                CalibrationScreen(
                    initialAllyRegions = CropRegions.allySlots,
                    initialEnemyRegions = CropRegions.enemySlots,
                    onSave = { ally, enemy ->
                        CropRegions.saveToPrefs(this, ally, enemy)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun CalibrationScreen(
    initialAllyRegions: List<CropRegion>,
    initialEnemyRegions: List<CropRegion>,
    onSave: (List<CropRegion>, List<CropRegion>) -> Unit
) {
    val context = LocalContext.current

    // FIX: wrap asset load in try-catch — sample_draft.png may not be bundled,
    // in which case we show a grey placeholder so the screen doesn't crash.
    val bitmap: Bitmap = remember {
        try {
            context.assets.open("sample_draft.png").use { stream ->
                BitmapFactory.decodeStream(stream)
                    ?: Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888)
            }
        } catch (_: Exception) {
            Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888)
        }
    }
    val imageBitmap = bitmap.asImageBitmap()
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    // Editable region lists
    var allyRegions by remember { mutableStateOf(initialAllyRegions.toMutableList()) }
    var enemyRegions by remember { mutableStateOf(initialEnemyRegions.toMutableList()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Image preview
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .onSizeChanged { imageSize = it }
            ) {
                Image(bitmap = imageBitmap, contentDescription = "Draft screenshot")

                // Overlay rectangles for ally slots (blue)
                allyRegions.forEachIndexed { _, region ->
                    if (imageSize != IntSize.Zero) {
                        val left = (region.left * imageSize.width).toFloat()
                        val top = (region.top * imageSize.height).toFloat()
                        val right = (region.right * imageSize.width).toFloat()
                        val bottom = (region.bottom * imageSize.height).toFloat()

                        Box(
                            modifier = Modifier
                                .offset(x = left.dp, y = top.dp)
                                .size(
                                    width = (right - left).dp,
                                    height = (bottom - top).dp
                                )
                                .background(Color.Blue.copy(alpha = 0.2f))
                                .border(1.dp, Color.Blue, RoundedCornerShape(2.dp))
                        )
                    }
                }

                // Overlay rectangles for enemy slots (red)
                enemyRegions.forEachIndexed { _, region ->
                    if (imageSize != IntSize.Zero) {
                        val left = (region.left * imageSize.width).toFloat()
                        val top = (region.top * imageSize.height).toFloat()
                        val right = (region.right * imageSize.width).toFloat()
                        val bottom = (region.bottom * imageSize.height).toFloat()

                        Box(
                            modifier = Modifier
                                .offset(x = left.dp, y = top.dp)
                                .size(
                                    width = (right - left).dp,
                                    height = (bottom - top).dp
                                )
                                .background(Color.Red.copy(alpha = 0.2f))
                                .border(1.dp, Color.Red, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Ally region editors
        item { Text("Your Team", style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(allyRegions) { index, region ->
            RegionEditor(
                label = "Ally ${index + 1}",
                region = region,
                onRegionChange = { newRegion ->
                    val newList = allyRegions.toMutableList()
                    newList[index] = newRegion
                    allyRegions = newList
                }
            )
        }

        // Enemy region editors
        item { Text("Enemy Team", style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(enemyRegions) { index, region ->
            RegionEditor(
                label = "Enemy ${index + 1}",
                region = region,
                onRegionChange = { newRegion ->
                    val newList = enemyRegions.toMutableList()
                    newList[index] = newRegion
                    enemyRegions = newList
                }
            )
        }

        // Save & Reset buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onSave(allyRegions, enemyRegions) },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }

                OutlinedButton(
                    onClick = {
                        allyRegions = CropRegions.DEFAULT_ALLY_SLOTS.toMutableList()
                        enemyRegions = CropRegions.DEFAULT_ENEMY_SLOTS.toMutableList()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset to Default") }
            }
        }
    }
}

@Composable
fun RegionEditor(
    label: String,
    region: CropRegion,
    onRegionChange: (CropRegion) -> Unit
) {
    var left by remember { mutableStateOf(region.left.toString()) }
    var top by remember { mutableStateOf(region.top.toString()) }
    var right by remember { mutableStateOf(region.right.toString()) }
    var bottom by remember { mutableStateOf(region.bottom.toString()) }

    fun update() {
        val newRegion = CropRegion(
            left.toFloatOrNull() ?: region.left,
            top.toFloatOrNull() ?: region.top,
            right.toFloatOrNull() ?: region.right,
            bottom.toFloatOrNull() ?: region.bottom
        )
        onRegionChange(newRegion)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = left,
                    onValueChange = { left = it; update() },
                    label = { Text("Left") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = top,
                    onValueChange = { top = it; update() },
                    label = { Text("Top") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = right,
                    onValueChange = { right = it; update() },
                    label = { Text("Right") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = bottom,
                    onValueChange = { bottom = it; update() },
                    label = { Text("Bottom") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}
