package com.gui.iai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import com.gui.iai.ui.theme.Iai

/**
 * Folha de modelos dos personagens para conferir no painel de Preview do
 * Android Studio, sem precisar jogar: cada linha é um personagem e cada
 * coluna uma pose (guarda, meio do saque, golpe, ajoelhando, caído).
 */
@Preview(name = "Folha de personagens", widthDp = 420, heightDp = 760)
@Composable
private fun SamuraiModelSheet() {
    val styles = SamuraiStyle.entries
    val accents = listOf(Iai.Shu, Iai.Ai, Iai.Ash, Iai.ShuBright, Iai.Washi, Iai.Kin)
    // draw, trail, fall
    val poses = listOf(
        Triple(0f, 0f, 0f),
        Triple(0.6f, 1f, 0f),
        Triple(1f, 0.6f, 0f),
        Triple(0f, 0f, 0.4f),
        Triple(0f, 0f, 1f)
    )
    Canvas(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Iai.SkyHigh, Iai.SkyMid, Iai.SkyLow)))
    ) {
        val cellW = size.width / poses.size
        val cellH = size.height / styles.size
        styles.forEachIndexed { row, style ->
            poses.forEachIndexed { col, (draw, trail, fall) ->
                drawSamurai(
                    feet = Offset(cellW * col + cellW * 0.42f, cellH * (row + 1) - cellH * 0.08f),
                    height = cellH * 0.72f,
                    facingRight = true,
                    style = style,
                    draw = draw,
                    crouch = 0f,
                    fall = fall,
                    wind = 0.6f,
                    time = 0.4f,
                    trail = trail,
                    colors = SamuraiColors(
                        ink = Iai.Sumi,
                        detail = Iai.SumiLine,
                        accent = accents[row],
                        rim = Iai.Rim,
                        steel = Iai.Steel,
                        steelEdge = Iai.SteelEdge,
                        gold = Iai.Kin
                    )
                )
            }
        }
    }
}

@Preview(name = "Cena do duelo", widthDp = 400, heightDp = 860)
@Composable
private fun DuelScenePreview() {
    DuelScene(
        state = SceneState(
            rightStyle = SamuraiStyle.ONI,
            rightFall = 0f,
            tension = 0.3f
        )
    )
}
