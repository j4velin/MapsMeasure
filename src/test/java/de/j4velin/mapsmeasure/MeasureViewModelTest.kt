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
import androidx.lifecycle.SavedStateHandle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelTest {

    private class FakeSettings : Settings {
        override var metric = true
        override var mapLayer = MapLayer.MAP
        override var lastCamera: CameraPosition? = null
    }

    private class FakeLocator : Locator {
        var location: LatLng? = null
        var places = emptyMap<String, LatLng>()
        override suspend fun currentLocation() = location
        override suspend fun findPlace(name: String) = places[name]
    }

    // android.net.Uri can not be created in unit tests, so loading, saving and sharing are only
    // covered by TraceFileTest and the UI tests
    private class FakeStorage : TraceStorage {
        override suspend fun load(uri: Uri) = throw IOException("not in unit tests")
        override suspend fun save(uri: Uri, trace: List<LatLng>) = throw IOException("not in unit tests")
        override suspend fun saveForSharing(trace: List<LatLng>) = throw IOException("not in unit tests")
        override suspend fun oldFiles() = emptyList<File>()
        override suspend fun delete(file: File) {}
    }

    private val a = LatLng(52.5163, 13.3777)
    private val b = LatLng(52.5170, 13.3790)

    private val settings = FakeSettings()
    private val locator = FakeLocator()
    private val storage = FakeStorage()
    private val savedState = SavedStateHandle()

    private fun viewModel() = MeasureViewModel(savedState, settings, locator, storage)

    private suspend fun MeasureViewModel.nextEvent() = withTimeoutOrNull(1000) { events.first() }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun settingsAreReadAndStored() {
        settings.metric = false
        settings.mapLayer = MapLayer.SATELLITE
        val vm = viewModel()
        assertEquals(false, vm.uiState.value.metric)
        assertEquals(MapLayer.SATELLITE, vm.uiState.value.mapLayer)

        vm.setMetric(true)
        vm.setMapLayer(MapLayer.TERRAIN)
        assertEquals(true, settings.metric)
        assertEquals(MapLayer.TERRAIN, settings.mapLayer)
        assertEquals(MapLayer.TERRAIN, vm.uiState.value.mapLayer)
    }

    @Test
    fun traceAndTypeSurviveRecreation() {
        viewModel().apply {
            addPoint(a)
            addPoint(b)
            setType(MeasureType.AREA)
        }
        val recreated = viewModel()
        assertEquals(listOf(a, b), recreated.uiState.value.trace)
        assertEquals(MeasureType.AREA, recreated.uiState.value.type)
    }

    @Test
    fun cameraIsStoredEachTimeTheMapStops() {
        val vm = viewModel()
        val position = CameraPosition.fromLatLngZoom(a, 12f)
        vm.onCameraIdle(position)
        assertEquals(position, vm.camera)
        assertEquals(position, settings.lastCamera)
    }

    @Test
    fun lastPositionIsTheOneFromBeforeTheMapWasShown() = runTest {
        settings.lastCamera = CameraPosition.fromLatLngZoom(a, 14f)
        val vm = viewModel()
        // the map reports its default position first
        vm.onCameraIdle(CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 0f))
        vm.moveToLastPosition()
        assertEquals(MeasureEvent.MoveCamera(a, 14f), vm.nextEvent())
    }

    @Test
    fun startCentersOnLocationOnlyIfNotZoomedIn() = runTest {
        locator.location = a
        val vm = viewModel()
        vm.centerOnCurrentLocation()
        assertEquals(MeasureEvent.MoveCamera(a, 16f), vm.nextEvent())

        vm.onCameraIdle(CameraPosition.fromLatLngZoom(b, 12f))
        vm.centerOnCurrentLocation()
        assertNull(vm.nextEvent())
    }

    @Test
    fun startWithoutKnownLocationMovesToLastPosition() = runTest {
        settings.lastCamera = CameraPosition.fromLatLngZoom(b, 11f)
        val vm = viewModel()
        vm.centerOnCurrentLocation()
        assertEquals(MeasureEvent.MoveCamera(b, 11f), vm.nextEvent())
    }

    @Test
    fun myLocationButtonMovesToLocationThenAddsPoint() = runTest {
        locator.location = a
        val vm = viewModel()
        vm.onMyLocationButton()
        assertEquals(MeasureEvent.MoveCamera(a, 16f), vm.nextEvent())
        assertTrue(vm.uiState.value.trace.isEmpty())

        vm.onCameraIdle(CameraPosition.fromLatLngZoom(a, 16f))
        vm.onMyLocationButton()
        assertEquals(MeasureEvent.Message(R.string.marker_on_current_location), vm.nextEvent())
        assertEquals(listOf(a), vm.uiState.value.trace)
    }

    @Test
    fun myLocationButtonWithoutKnownLocationSaysSo() = runTest {
        val vm = viewModel()
        vm.onMyLocationButton()
        assertEquals(MeasureEvent.Message(R.string.no_location_found), vm.nextEvent())
    }

    @Test
    fun searchMovesToPlaceWithAtLeastZoom10() = runTest {
        locator.places = mapOf("Berlin" to a)
        val vm = viewModel()
        vm.search("Berlin")
        assertEquals(MeasureEvent.MoveCamera(a, 10f, animate = true), vm.nextEvent())

        vm.onCameraIdle(CameraPosition.fromLatLngZoom(b, 15f))
        vm.search("Berlin")
        assertEquals(MeasureEvent.MoveCamera(a, 15f, animate = true), vm.nextEvent())

        vm.search("Atlantis")
        assertEquals(MeasureEvent.Message(R.string.no_location_found), vm.nextEvent())
    }
}
