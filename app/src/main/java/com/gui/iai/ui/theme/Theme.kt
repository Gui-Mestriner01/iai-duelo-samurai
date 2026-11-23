package com.gui.iai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val IaiColors = lightColorScheme(
    primary = Iai.Sumi,
    onPrimary = Iai.Washi,
    secondary = Iai.Shu,
    onSecondary = Iai.Washi,
    tertiary = Iai.Kin,
    background = Iai.Washi,
    onBackground = Iai.Sumi,
    surface = Iai.Washi,
    onSurface = Iai.Sumi,
    surfaceVariant = Iai.WashiDeep,
    onSurfaceVariant = Iai.SumiSoft,
    outline = Iai.SumiFaded,
    error = Iai.Shu
)

@Composable
fun IaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IaiColors,
        typography = IaiTypography,
        content = content
    )
}
