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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private enum class OpenDialog { NONE, UNITS, SAVE, OLD_TRACES, ABOUT, DELETE_ALL }

/**
 * The only screen: the map with the measured value on top and the menu in a drawer. It connects
 * the [MeasureViewModel] with the map, the drawer and the dialogs.
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

    val locationPermission = rememberLocationPermission(
        onGranted = { action ->
            when (action) {
                LocationAction.CENTER_ON_START -> viewModel.centerOnCurrentLocation()
                LocationAction.MY_LOCATION_BUTTON -> viewModel.onMyLocationButton()
            }
        },
        onDenied = { viewModel.moveToLastPosition() },
    )

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
            else locationPermission.request(LocationAction.CENTER_ON_START)
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
    val zoom: (CameraUpdate) -> Unit = { update ->
        if (mapLoaded) scope.launch { cameraPositionState.animate(update) }
    }

    MeasureDrawer(
        permanent = permanentDrawer,
        drawerState = drawerState,
        drawerContent = {
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
                onMapLayer = {
                    viewModel.setMapLayer(it)
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
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            MeasureMap(
                state = state,
                cameraPositionState = cameraPositionState,
                hasLocationPermission = locationPermission.granted,
                onMapLoaded = { mapLoaded = true },
                onAddPoint = { viewModel.addPoint(it) },
            )
            MapControls(
                value = state.formattedValue(),
                showMenuButton = !permanentDrawer,
                onToggleType = { viewModel.toggleType() },
                onRemoveLast = { viewModel.removeLastPoint() },
                onClearAll = { dialog = OpenDialog.DELETE_ALL },
                onMyLocation = { locationPermission.request(LocationAction.MY_LOCATION_BUTTON) },
                onZoomIn = { zoom(CameraUpdateFactory.zoomIn()) },
                onZoomOut = { zoom(CameraUpdateFactory.zoomOut()) },
                onMenu = { scope.launch { drawerState.open() } },
            )
        }
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
