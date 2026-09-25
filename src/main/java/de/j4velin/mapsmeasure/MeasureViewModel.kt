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

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.math.max

/**
 * One-off things the screen should do, as opposed to the state it shows
 */
sealed interface MeasureEvent {
    data class MoveCamera(val target: LatLng, val zoom: Float, val animate: Boolean = false) :
        MeasureEvent

    data class Message(@StringRes val text: Int) : MeasureEvent

    data class Error(val exception: Exception) : MeasureEvent

    /** a trace was written to a file which can be shared by the given content uri */
    data class Share(val uri: Uri) : MeasureEvent
}

/**
 * Holds the trace and settings of the measure screen. The trace, the measure type and the camera
 * are kept in the SavedStateHandle, so they survive rotation and process death; the units and the
 * map type are settings and stored in the [Settings].
 */
class MeasureViewModel(
    private val savedState: SavedStateHandle,
    private val settings: Settings,
    private val locator: Locator,
    private val storage: TraceStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MeasureUiState(
            trace = savedState.get<List<LatLng>>(KEY_TRACE) ?: emptyList(),
            type = savedState.get<String>(KEY_TYPE)
                ?.let { name -> MeasureType.entries.firstOrNull { it.name == name } }
                ?: MeasureType.DISTANCE,
            metric = settings.metric,
            mapType = settings.mapType,
        )
    )
    val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

    // read before the map reports its first position, which overwrites the stored one
    private val previousCamera = settings.lastCamera

    private val _events = Channel<MeasureEvent>(Channel.BUFFERED)
    val events: Flow<MeasureEvent> = _events.receiveAsFlow()

    /**
     * Where the map was when it last stopped moving, or null if it has not been shown yet
     */
    var camera: CameraPosition?
        get() = savedState[KEY_CAMERA]
        private set(value) {
            savedState[KEY_CAMERA] = value
        }

    /**
     * @return true only the first time it is called for this screen, not after it got recreated
     */
    fun consumeFirstStart(): Boolean {
        if (savedState.get<Boolean>(KEY_STARTED) == true) return false
        savedState[KEY_STARTED] = true
        return true
    }

    private fun setTrace(trace: List<LatLng>) {
        savedState[KEY_TRACE] = ArrayList(trace)
        _uiState.update { it.copy(trace = trace) }
    }

    fun addPoint(point: LatLng) = setTrace(uiState.value.trace + point)

    fun removeLastPoint() {
        val trace = uiState.value.trace
        if (trace.isNotEmpty()) setTrace(trace.dropLast(1))
    }

    fun clear() = setTrace(emptyList())

    fun setType(type: MeasureType) {
        savedState[KEY_TYPE] = type.name
        _uiState.update { it.copy(type = type) }
    }

    fun toggleType() = setType(
        if (uiState.value.type == MeasureType.DISTANCE) MeasureType.AREA else MeasureType.DISTANCE
    )

    fun setMetric(metric: Boolean) {
        settings.metric = metric
        _uiState.update { it.copy(metric = metric) }
    }

    /**
     * @param mapType one of GoogleMap.MAP_TYPE_NORMAL, MAP_TYPE_HYBRID or MAP_TYPE_TERRAIN
     */
    fun setMapType(mapType: Int) {
        settings.mapType = mapType
        _uiState.update { it.copy(mapType = mapType) }
    }

    fun onCameraIdle(position: CameraPosition) {
        camera = position
        // stored right away, as the process might be killed without any further callback
        settings.lastCamera = position
    }

    /**
     * Replaces the trace with the one stored in the given file
     */
    fun loadTrace(uri: Uri) {
        viewModelScope.launch {
            try {
                val loaded = storage.load(uri)
                setTrace(loaded)
                loaded.firstOrNull()?.let { _events.send(MeasureEvent.MoveCamera(it, 16f)) }
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) Log.d(LOG_TAG, "can not load $uri", e)
                _events.send(MeasureEvent.Error(e))
            }
        }
    }

    /**
     * Writes the trace to the given file, e.g. one the user created with the system file picker
     */
    fun saveTrace(uri: Uri) {
        val trace = uiState.value.trace
        viewModelScope.launch {
            try {
                storage.save(uri, trace)
                _events.send(MeasureEvent.Message(R.string.file_saved))
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) Log.d(LOG_TAG, "can not save $uri", e)
                _events.send(MeasureEvent.Error(e))
            }
        }
    }

    /**
     * Writes the trace to a temporary file and asks the screen to share it
     */
    fun shareTrace() {
        val trace = uiState.value.trace
        viewModelScope.launch {
            try {
                _events.send(MeasureEvent.Share(storage.saveForSharing(trace)))
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) Log.d(LOG_TAG, "can not share", e)
                _events.send(MeasureEvent.Error(e))
            }
        }
    }

    /**
     * Traces which app versions before 2.0 saved in the app's own folders
     */
    suspend fun oldTraceFiles(): List<File> = storage.oldFiles()

    fun deleteOldTrace(file: File) {
        viewModelScope.launch { storage.delete(file) }
    }

    /**
     * Searches for the given place and moves the map there
     */
    fun search(query: String) {
        viewModelScope.launch {
            val place = locator.findPlace(query)
            if (place == null) {
                if (BuildConfig.DEBUG) Log.d(LOG_TAG, "no location found")
                _events.send(MeasureEvent.Message(R.string.no_location_found))
            } else {
                _events.send(MeasureEvent.MoveCamera(place, max(10f, camera?.zoom ?: 0f), animate = true))
            }
        }
    }

    /**
     * Moves the map to the current location, unless the user already zoomed in somewhere. If the
     * location is not known, the map moves to where it was last time instead.
     * Needs the location permission.
     */
    fun centerOnCurrentLocation() {
        viewModelScope.launch {
            val location = locator.currentLocation()
            if (location == null) {
                moveToLastPosition()
            } else if ((camera?.zoom ?: 0f) <= 5) {
                _events.send(MeasureEvent.MoveCamera(location, 16f))
            }
        }
    }

    /**
     * Moves the map to the current location or, if it is already there, adds a point at it.
     * Needs the location permission.
     */
    fun onMyLocationButton() {
        viewModelScope.launch {
            val location = locator.currentLocation()
            if (location == null) {
                _events.send(MeasureEvent.Message(R.string.no_location_found))
                return@launch
            }
            val target = camera?.target
            // Only if the distance is less than 50cm we are on our location, add the marker
            if (target != null && SphericalUtil.computeDistanceBetween(location, target) < 0.5) {
                _events.send(MeasureEvent.Message(R.string.marker_on_current_location))
                addPoint(location)
            } else {
                _events.send(MeasureEvent.MoveCamera(location, 16f))
            }
        }
    }

    /**
     * Moves the map to where it was when the app was used last time. Used when the user does
     * not grant the location permission.
     */
    fun moveToLastPosition() {
        val last = previousCamera ?: return
        _events.trySend(MeasureEvent.MoveCamera(last.target, last.zoom))
    }

    companion object {
        private const val KEY_TRACE = "trace"
        private const val KEY_TYPE = "type"
        private const val KEY_CAMERA = "camera"
        private const val KEY_STARTED = "started"

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                MeasureViewModel(
                    createSavedStateHandle(),
                    PreferenceSettings(app),
                    PlayServicesLocator(app),
                    FileTraceStorage(app),
                )
            }
        }
    }
}
