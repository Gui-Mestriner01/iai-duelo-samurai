package com.gui.iai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import com.gui.iai.domain.Arena
import com.gui.iai.ui.theme.Iai

/**
 * Folha de modelos dos personagens para conferir no painel de Preview do
 * Android Studio, sem precisar jogar: cada linha é um personagem e cada
 * coluna um momento: guarda, antecipação, meio do saque, zanshin, chiburi,
 * noto (embainhando), tranco do corte e caído.
 */
@Preview(name = "Folha de personagens", widthDp = 640, heightDp = 1200)
@Composable
private fun SamuraiModelSheet() {
    SamuraiSheet(
        listOf(
            -1f to 0f,
            60f to 0f,
            150f to 0f,
            600f to 0f,
            1080f to 0f,
            1450f to 0f,
            1850f to 0f,
            -1f to 1f
        )
    )
}

/** A morte quadro a quadro: tranco, cambaleio, joelhos, tombo, chão e o fim de cada chefe. */
@Preview(name = "Animação de morte", widthDp = 640, heightDp = 1200)
@Composable
private fun SamuraiDeathSheet() {
    SamuraiSheet(listOf(-1f to 0.06f, -1f to 0.2f, -1f to 0.38f, -1f to 0.52f, -1f to 0.64f, -1f to 0.76f, -1f to 0.86f, -1f to 1f))
}

@Composable
private fun SamuraiSheet(poses: List<Pair<Float, Float>>) {
    val styles = SamuraiStyle.entries
    val accents = listOf(Iai.Shu, Iai.Ai, Iai.Ash, Iai.ShuBright, Iai.Washi, Iai.Kin, Iai.Ice, Iai.ShuBright, Iai.Yoru, Iai.Ai)
    Canvas(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Iai.SkyMid, Iai.SkyLow, Iai.Sun)))
    ) {
        val cellW = size.width / poses.size
        val cellH = size.height / styles.size
        styles.forEachIndexed { row, style ->
            poses.forEachIndexed { col, (strikeMs, fall) ->
                drawSamurai(
                    feet = Offset(cellW * col + cellW * 0.42f, cellH * (row + 1) - cellH * 0.08f),
                    height = cellH * 0.72f,
                    facingRight = true,
                    style = style,
                    strikeMs = strikeMs,
                    crouch = 0f,
                    fall = fall,
                    wind = 0.6f,
                    time = 0.4f,
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
            rightStyle = SamuraiStyle.KAGE,
            rightObi = Iai.Yoru,
            rightFall = 0f,
            tension = 0.3f,
            arena = Arena.NIGHT
        )
    )
}

@Preview(name = "Arena de neve", widthDp = 400, heightDp = 860)
@Composable
private fun SnowScenePreview() {
    DuelScene(
        state = SceneState(
            rightStyle = SamuraiStyle.YUKI,
            rightObi = Iai.Ice,
            arena = Arena.SNOW,
            blizzard = true,
            tension = 0.5f
        )
    )
}

@Preview(name = "Tengu", widthDp = 400, heightDp = 860)
@Composable
private fun TenguScenePreview() {
    DuelScene(
        state = SceneState(
            rightStyle = SamuraiStyle.TENGU,
            rightObi = Iai.ShuBright
        )
    )
}
