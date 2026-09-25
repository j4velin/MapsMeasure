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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline

private val LineColor = Color(0x80000000)
private val LineColorDark = Color(0x80FFFFFF)
private val AreaColor = Color(0x80FF0000)

/**
 * The map with the trace on it. Tapping the map or a point of the trace adds a point.
 */
@Composable
internal fun MeasureMap(
    state: MeasureUiState,
    cameraPositionState: CameraPositionState,
    hasLocationPermission: Boolean,
    onMapLoaded: () -> Unit,
    onAddPoint: (LatLng) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapType = when (state.mapLayer) {
                MapLayer.MAP -> MapType.NORMAL
                MapLayer.SATELLITE -> MapType.HYBRID
                MapLayer.TERRAIN -> MapType.TERRAIN
            },
        ),
        // the buttons of the map itself are always light, so the map shows none and MapControls
        // has its own which follow the theme
        uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
        // the dark map style only applies to the normal and terrain map
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
        // keeps the map controls out from under the system bars
        contentPadding = WindowInsets.systemBars.asPaddingValues(),
        onMapLoaded = onMapLoaded,
        onMapClick = onAddPoint,
    ) {
        val icon = remember { BitmapDescriptorFactory.fromResource(R.drawable.marker) }
        state.trace.forEachIndexed { index, point ->
            // points only change at the end of the trace, so the ones before are kept
            key(index, point) {
                Marker(
                    state = remember { MarkerState(position = point) },
                    icon = icon,
                    flat = true,
                    anchor = Offset(0.5f, 0.5f),
                    onClick = {
                        onAddPoint(it.position)
                        true
                    },
                )
            }
        }
        if (state.trace.size >= 2) {
            Polyline(points = state.trace, color = if (dark) LineColorDark else LineColor, width = 5f)
        }
        if (state.type == MeasureType.AREA && state.trace.size >= 3) {
            Polygon(points = state.trace, fillColor = AreaColor, strokeWidth = 0f)
        }
    }
}

/**
 * Everything on top of the map: the measured value, the location and zoom buttons and, if the
 * drawer is not shown permanently, the button to open it
 */
@Composable
internal fun MapControls(
    value: String,
    showMenuButton: Boolean,
    onToggleType: () -> Unit,
    onRemoveLast: () -> Unit,
    onClearAll: () -> Unit,
    onMyLocation: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onMenu: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ValueBox(
            value = value,
            onToggleType = onToggleType,
            onRemoveLast = onRemoveLast,
            onClearAll = onClearAll,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp),
        )
        MapButton(
            icon = R.drawable.ic_my_location,
            description = R.string.my_location,
            onClick = onMyLocation,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(top = 10.dp, end = 10.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(end = 10.dp, bottom = 44.dp),
        ) {
            MapButton(R.drawable.ic_zoom_in, R.string.zoom_in, onZoomIn)
            MapButton(R.drawable.ic_zoom_out, R.string.zoom_out, onZoomOut)
        }
        if (showMenuButton) {
            MapButton(
                icon = R.drawable.ic_menu,
                description = R.string.menu,
                onClick = onMenu,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    // above the Google logo, which the map places in the bottom left corner
                    .padding(start = 10.dp, bottom = 44.dp),
            )
        }
    }
}

/**
 * The measured value at the top of the screen. Tapping the value switches between distance and
 * area, tapping the trash icon removes the last point and a long press on it asks to remove all.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ValueBox(
    value: String,
    onToggleType: () -> Unit,
    onRemoveLast: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onToggleType)
                    .padding(10.dp),
            )
            Icon(
                painter = painterResource(R.drawable.ic_action_delete),
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .combinedClickable(onClick = onRemoveLast, onLongClick = onClearAll)
                    .padding(end = 10.dp),
            )
        }
    }
}

/**
 * A button on top of the map, e.g. to open the drawer or to zoom. Looks like the [ValueBox].
 */
@Composable
private fun MapButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(10.dp),
        )
    }
}
