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

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap as GoogleMapView
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
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private val LineColor = Color(0x80000000)
private val LineColorDark = Color(0x80FFFFFF)
private val AreaColor = Color(0x80FF0000)
private val DrawerWidth = 260.dp

private enum class OpenDialog { NONE, UNITS, SAVE, OLD_TRACES, ABOUT, DELETE_ALL }

/**
 * The only screen: the map with the measured value on top and the menu in a drawer
 *
 * @param openedFile a trace to load when the screen starts for the first time
 */
@Composable
fun MeasureScreen(viewModel: MeasureViewModel, openedFile: Uri?) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var dialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }
    var mapLoaded by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        viewModel.camera?.let { position = it }
    }

    // location permission
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasLocationPermission = context.hasLocationPermission()
            if (hasLocationPermission) pendingLocationAction?.invoke()
            else viewModel.moveToLastPosition()
            pendingLocationAction = null
        }
    val withLocationPermission: (() -> Unit) -> Unit = { action ->
        if (context.hasLocationPermission()) {
            action()
        } else {
            pendingLocationAction = action
            permissionRequest.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    // files
    val createDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri?.let { viewModel.saveTrace(it) }
        }
    val openDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.loadTrace(it) }
        }
    var oldFiles by remember { mutableStateOf(emptyList<File>()) }
    LaunchedEffect(dialog) {
        if (dialog == OpenDialog.SAVE || dialog == OpenDialog.OLD_TRACES) {
            oldFiles = viewModel.oldTraceFiles()
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.consumeFirstStart()) {
            if (openedFile != null) viewModel.loadTrace(openedFile)
            else withLocationPermission { viewModel.centerOnCurrentLocation() }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MeasureEvent.MoveCamera -> {
                    // CameraUpdateFactory can not be used before the map is initialized
                    snapshotFlow { mapLoaded }.first { it }
                    val update = CameraUpdateFactory.newLatLngZoom(event.target, event.zoom)
                    if (event.animate) cameraPositionState.animate(update)
                    else cameraPositionState.move(update)
                }
                is MeasureEvent.Message ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                is MeasureEvent.Error -> context.showError(event.exception)
                is MeasureEvent.Share -> {
                    val share = Intent(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_STREAM, event.uri)
                        .setType("text/comma-separated-values")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(share, null))
                }
            }
        }
    }

    // the view model needs the camera position, e.g. to restore it after process death
    LaunchedEffect(cameraPositionState.isMoving, mapLoaded) {
        if (mapLoaded && !cameraPositionState.isMoving) {
            viewModel.onCameraIdle(cameraPositionState.position)
        }
    }

    // landscape and at least 600dp wide: the drawer is always shown
    val windowSize = LocalWindowInfo.current.containerSize
    val permanentDrawer = windowSize.width > windowSize.height &&
            with(LocalDensity.current) { windowSize.width.toDp() } >= 600.dp
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    val drawerItems: @Composable () -> Unit = {
        DrawerItems(
            state = state,
            onSearch = {
                viewModel.search(it)
                closeDrawer()
            },
            onUnits = {
                dialog = OpenDialog.UNITS
                closeDrawer()
            },
            onType = {
                viewModel.setType(it)
                closeDrawer()
            },
            onMapType = {
                viewModel.setMapType(it)
                closeDrawer()
            },
            onSave = {
                dialog = OpenDialog.SAVE
                closeDrawer()
            },
            onMoreApps = { context.openMoreApps() },
            onAbout = {
                dialog = OpenDialog.ABOUT
                closeDrawer()
            },
        )
    }

    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            MeasureMap(
                state = state,
                cameraPositionState = cameraPositionState,
                hasLocationPermission = hasLocationPermission,
                onMapLoaded = { mapLoaded = true },
                onAddPoint = { viewModel.addPoint(it) },
                onMyLocationButton = { withLocationPermission { viewModel.onMyLocationButton() } },
            )
            ValueBox(
                value = state.formattedValue(),
                onToggleType = { viewModel.toggleType() },
                onRemoveLast = { viewModel.removeLastPoint() },
                onClearAll = { dialog = OpenDialog.DELETE_ALL },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp),
            )
            if (!permanentDrawer) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = stringResource(R.string.menu),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp)
                        .clickable { scope.launch { drawerState.open() } },
                )
            }
        }
    }

    if (permanentDrawer) {
        PermanentNavigationDrawer(
            drawerContent = { PermanentDrawerSheet(Modifier.width(DrawerWidth)) { drawerItems() } },
            content = content,
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // swiping would otherwise open the drawer while panning the map
            gesturesEnabled = drawerState.isOpen,
            drawerContent = { ModalDrawerSheet(Modifier.width(DrawerWidth)) { drawerItems() } },
            content = content,
        )
    }

    val dismiss = { dialog = OpenDialog.NONE }
    when (dialog) {
        OpenDialog.NONE -> {}
        OpenDialog.UNITS -> UnitsDialog(
            metric = state.metric,
            distance = state.distance,
            area = state.area,
            onMetricChange = { viewModel.setMetric(it) },
            onDismiss = dismiss,
        )
        OpenDialog.SAVE -> SaveDialog(
            hasOldFiles = oldFiles.isNotEmpty(),
            onSave = {
                dismiss()
                context.launchOrShowError {
                    createDocument.launch("MapsMeasure_${System.currentTimeMillis()}.csv")
                }
            },
            onLoad = {
                dismiss()
                context.launchOrShowError { openDocument.launch(arrayOf("*/*")) }
            },
            onShare = {
                dismiss()
                viewModel.shareTrace()
            },
            onOldFiles = { dialog = OpenDialog.OLD_TRACES },
            onDismiss = dismiss,
        )
        OpenDialog.OLD_TRACES -> OldTracesDialog(
            files = oldFiles,
            onLoad = {
                dismiss()
                viewModel.loadTrace(Uri.fromFile(it))
            },
            onDelete = {
                viewModel.deleteOldTrace(it)
                oldFiles = oldFiles - it
                if (oldFiles.isEmpty()) dismiss()
            },
            onDismiss = dismiss,
        )
        OpenDialog.ABOUT -> AboutDialog(onDismiss = dismiss)
        OpenDialog.DELETE_ALL -> DeleteAllDialog(
            count = state.trace.size,
            onConfirm = {
                viewModel.clear()
                dismiss()
            },
            onDismiss = dismiss,
        )
    }
}

@Composable
private fun MeasureMap(
    state: MeasureUiState,
    cameraPositionState: CameraPositionState,
    hasLocationPermission: Boolean,
    onMapLoaded: () -> Unit,
    onAddPoint: (LatLng) -> Unit,
    onMyLocationButton: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapType = MapType.entries.firstOrNull { it.value == state.mapType } ?: MapType.NORMAL,
        ),
        uiSettings = MapUiSettings(myLocationButtonEnabled = true),
        // the dark map style only applies to the normal and terrain map
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
        // keeps the map controls out from under the system bars
        contentPadding = WindowInsets.systemBars.asPaddingValues(),
        onMapLoaded = onMapLoaded,
        onMapClick = onAddPoint,
        onMyLocationButtonClick = {
            onMyLocationButton()
            true
        },
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

@Composable
internal fun DrawerItems(
    state: MeasureUiState,
    onSearch: (String) -> Unit,
    onUnits: () -> Unit,
    onType: (MeasureType) -> Unit,
    onMapType: (Int) -> Unit,
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
        DrawerItem(
            R.drawable.ic_mapview_map, R.string.mapview_map,
            selected = state.mapType == GoogleMapView.MAP_TYPE_NORMAL,
            onClick = { onMapType(GoogleMapView.MAP_TYPE_NORMAL) },
        )
        DrawerItem(
            R.drawable.ic_mapview_satellite, R.string.mapview_satellite,
            selected = state.mapType == GoogleMapView.MAP_TYPE_HYBRID,
            onClick = { onMapType(GoogleMapView.MAP_TYPE_HYBRID) },
        )
        DrawerItem(
            R.drawable.ic_mapview_terrain, R.string.mapview_terrain,
            selected = state.mapType == GoogleMapView.MAP_TYPE_TERRAIN,
            onClick = { onMapType(GoogleMapView.MAP_TYPE_TERRAIN) },
        )

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

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

private fun Context.showError(e: Exception) {
    Toast.makeText(
        this,
        getString(R.string.error, e.javaClass.simpleName + "\n" + e.message),
        Toast.LENGTH_LONG
    ).show()
}

/**
 * Starts a system activity, e.g. the file picker, which might not exist on some devices
 */
private fun Context.launchOrShowError(launch: () -> Unit) {
    try {
        launch()
    } catch (e: ActivityNotFoundException) {
        showError(e)
    }
}

private fun Context.openMoreApps() {
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW, "market://search?q=pub:j4velin".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (anf: ActivityNotFoundException) {
        startActivity(
            Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/developer?id=j4velin".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
