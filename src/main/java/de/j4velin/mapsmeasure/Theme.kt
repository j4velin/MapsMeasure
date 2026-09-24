/*
 * Copyright 2026 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.j4velin.mapsmeasure

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// the greys of the old UI
private val LightColors = lightColorScheme(
    primary = Color(0xFF555555),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFDDDDDD),
    onSecondaryContainer = Color.Black,
    surface = Color(0xFFF2F2F2),
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF666666),
    outline = Color(0xFF999999),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCCCCCC),
    onPrimary = Color.Black,
    secondaryContainer = Color(0xFF4A4A4A),
    onSecondaryContainer = Color.White,
    surface = Color(0xFF222222),
    onSurface = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFFBBBBBB),
    outline = Color(0xFF777777),
)

@Composable
fun MapsMeasureTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
