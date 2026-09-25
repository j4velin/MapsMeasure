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

import android.location.Geocoder
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private val DrawerWidth = 260.dp

/**
 * Shows the drawer next to the content if it is [permanent], otherwise it slides in over it
 */
@Composable
internal fun MeasureDrawer(
    permanent: Boolean,
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (permanent) {
        PermanentNavigationDrawer(
            drawerContent = { PermanentDrawerSheet(Modifier.width(DrawerWidth)) { drawerContent() } },
            content = content,
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // swiping would otherwise open the drawer while panning the map
            gesturesEnabled = drawerState.isOpen,
            drawerContent = { ModalDrawerSheet(Modifier.width(DrawerWidth)) { drawerContent() } },
            content = content,
        )
    }
}

@Composable
internal fun DrawerItems(
    state: MeasureUiState,
    onSearch: (String) -> Unit,
    onUnits: () -> Unit,
    onType: (MeasureType) -> Unit,
    onMapLayer: (MapLayer) -> Unit,
    onSave: () -> Unit,
    onMoreApps: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (Geocoder.isPresent()) SearchField(onSearch)

        SectionHeader(R.string.section_measure)
        DrawerItem(R.drawable.ic_metric, R.string.units, selected = false, onClick = onUnits)
        DrawerItem(
            R.drawable.ic_distance, R.string.measure_distance,
            selected = state.type == MeasureType.DISTANCE,
            onClick = { onType(MeasureType.DISTANCE) },
        )
        DrawerItem(
            R.drawable.ic_area, R.string.measure_area,
            selected = state.type == MeasureType.AREA,
            onClick = { onType(MeasureType.AREA) },
        )

        SectionHeader(R.string.section_mapview)
        for (layer in MapLayer.entries) {
            val (icon, label) = when (layer) {
                MapLayer.MAP -> R.drawable.ic_mapview_map to R.string.mapview_map
                MapLayer.SATELLITE -> R.drawable.ic_mapview_satellite to R.string.mapview_satellite
                MapLayer.TERRAIN -> R.drawable.ic_mapview_terrain to R.string.mapview_terrain
            }
            DrawerItem(icon, label, selected = state.mapLayer == layer, onClick = { onMapLayer(layer) })
        }

        SectionHeader(R.string.about)
        DrawerItem(R.drawable.ic_action_save, R.string.savenshare, selected = false, onClick = onSave, small = true)
        DrawerItem(R.drawable.ic_store, R.string.moreapps, selected = false, onClick = onMoreApps, small = true)
        DrawerItem(R.drawable.ic_about, R.string.about, selected = false, onClick = onAbout, small = true)
    }
}

@Composable
private fun SearchField(onSearch: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        placeholder = { Text(stringResource(android.R.string.search_go)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            focusManager.clearFocus()
            if (query.isNotBlank()) onSearch(query)
        }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerItem(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    selected: Boolean,
    onClick: () -> Unit,
    small: Boolean = false,
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = stringResource(label),
                style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            )
        },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        selected = selected,
        onClick = onClick,
    )
}
