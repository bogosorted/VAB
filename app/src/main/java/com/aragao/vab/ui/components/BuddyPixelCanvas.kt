package com.aragao.vab.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.aragao.vab.core.buddy.BuddyDna
import com.aragao.vab.core.buddy.BuddyGenerator
import com.aragao.vab.core.buddy.BuddyVisualState
import kotlinx.coroutines.delay

@Composable
fun BuddyPixelCanvas(
    dna: BuddyDna,
    state: BuddyVisualState,
    audioAmplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(state) {
        while (true) {
            delay(if (state == BuddyVisualState.LISTENING) 120L else 300L)
            tick++
        }
    }

    val pixels = remember(dna.seed, state, tick, (audioAmplitude * 10).toInt()) {
        BuddyGenerator.renderFrame(
            dna = dna,
            state = state,
            tick = tick,
            audioAmplitude = audioAmplitude
        )
    }

    Canvas(modifier = modifier) {
        val grid = BuddyGenerator.GRID_SIZE
        val cellW = size.width / grid
        val cellH = size.height / grid

        for (y in 0 until grid) {
            for (x in 0 until grid) {
                val argb = pixels[y * grid + x]
                val alpha = (argb ushr 24) and 0xFF
                if (alpha > 0) {
                    drawRect(
                        color = Color(argb),
                        topLeft = Offset(x * cellW, y * cellH),
                        size = Size(cellW + 0.4f, cellH + 0.4f)
                    )
                }
            }
        }
    }
}
