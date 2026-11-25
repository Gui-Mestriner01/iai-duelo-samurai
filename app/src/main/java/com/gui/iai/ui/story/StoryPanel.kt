package com.gui.iai.ui.story

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gui.iai.domain.Opponent
import com.gui.iai.domain.Speaker
import com.gui.iai.domain.StoryLine
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.SamuraiColors
import com.gui.iai.ui.components.SamuraiStyle
import com.gui.iai.ui.components.accentColor
import com.gui.iai.ui.components.drawSamurai
import com.gui.iai.ui.components.sceneStyle
import com.gui.iai.ui.theme.BrushFont
import com.gui.iai.ui.theme.Iai
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cena de história em estilo mangá: quadro com o retrato de quem fala,
 * linhas de velocidade ao fundo e o balão com o texto aparecendo letra por letra.
 * Toque para avançar; "Pular" vai direto para o duelo.
 */
@Composable
fun StoryOverlay(
    opponent: Opponent,
    line: StoryLine,
    index: Int,
    total: Int,
    heroObi: Color,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    // Efeito máquina de escrever; o primeiro toque completa o texto
    var shown by remember(index) { mutableIntStateOf(0) }
    LaunchedEffect(index) {
        while (shown < line.text.length) {
            delay(22)
            shown++
        }
    }
    val complete = shown >= line.text.length

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Iai.Sumi.copy(alpha = 0.72f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (complete) onNext() else shown = line.text.length
            }
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        Text(
            text = "Pular ▸▸",
            style = MaterialTheme.typography.labelLarge,
            color = Iai.Washi,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onSkip)
                .padding(10.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = line.speaker,
                transitionSpec = {
                    (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith fadeOut(tween(150))
                },
                label = "storyPortrait"
            ) { speaker ->
                MangaPanel(
                    speaker = speaker,
                    opponent = opponent,
                    heroObi = heroObi,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .rotate(if (speaker == Speaker.HERO) -1.5f else 1.5f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Balão de fala
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Iai.Washi)
                    .border(3.dp, Iai.Sumi, RoundedCornerShape(22.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = speakerName(line.speaker, opponent).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (line.speaker == Speaker.OPPONENT) Iai.Shu else Iai.SumiSoft
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = line.text.take(shown),
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = if (line.speaker == Speaker.NARRATOR) FontStyle.Italic else FontStyle.Normal,
                    color = Iai.Sumi
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1} / $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = Iai.Washi.copy(alpha = 0.7f)
                )
                Text(
                    text = if (complete) "Toque para continuar ▸" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Iai.Washi
                )
            }
        }
    }
}

private fun speakerName(speaker: Speaker, opponent: Opponent): String = when (speaker) {
    Speaker.NARRATOR -> "Narrador"
    Speaker.HERO -> "Você"
    Speaker.OPPONENT -> opponent.displayName
}

/** Quadro de mangá com linhas de velocidade e o personagem em close. */
@Composable
private fun MangaPanel(
    speaker: Speaker,
    opponent: Opponent,
    heroObi: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Iai.Washi)
            .border(4.dp, Iai.Sumi, RoundedCornerShape(4.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawSpeedLines(seed = speaker.ordinal + opponent.ordinal * 3)
            val colors = { accent: Color ->
                SamuraiColors(
                    ink = Iai.Sumi,
                    detail = Iai.SumiLine,
                    accent = accent,
                    rim = Iai.Rim,
                    steel = Iai.Steel,
                    steelEdge = Iai.SteelEdge,
                    gold = Iai.Kin
                )
            }
            when (speaker) {
                Speaker.HERO -> drawSamurai(
                    feet = Offset(size.width * 0.42f, size.height * 2.05f),
                    height = size.height * 2.2f,
                    facingRight = true,
                    style = SamuraiStyle.PLAYER,
                    strikeMs = -1f,
                    crouch = 0.4f,
                    fall = 0f,
                    wind = 0.7f,
                    time = 0.8f,
                    colors = colors(heroObi)
                )

                Speaker.OPPONENT -> drawSamurai(
                    feet = Offset(size.width * 0.58f, size.height * 2.05f),
                    height = size.height * 2.2f,
                    facingRight = false,
                    style = opponent.sceneStyle(),
                    strikeMs = -1f,
                    crouch = 0.4f,
                    fall = 0f,
                    wind = 0.7f,
                    time = 0.8f,
                    colors = colors(opponent.accentColor())
                )

                Speaker.NARRATOR -> Unit
            }
        }
        if (speaker == Speaker.NARRATOR) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HankoSeal(text = opponent.kanji, size = 96.dp, stampIn = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = opponent.title.uppercase(),
                    fontFamily = BrushFont,
                    fontSize = 24.sp,
                    color = Iai.Sumi
                )
            }
        }
    }
}

/** Linhas de velocidade saindo do centro, como nos mangás. */
private fun DrawScope.drawSpeedLines(seed: Int) {
    val random = Random(seed)
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.maxDimension
    repeat(70) {
        val angle = random.nextFloat() * 6.2832f
        val inner = radius * (0.22f + random.nextFloat() * 0.18f)
        val start = center + Offset(cos(angle), sin(angle)) * inner
        val end = center + Offset(cos(angle), sin(angle)) * radius
        drawLine(
            color = Iai.Sumi.copy(alpha = 0.12f + random.nextFloat() * 0.2f),
            start = start,
            end = end,
            strokeWidth = 1f + random.nextFloat() * 3f
        )
    }
}
