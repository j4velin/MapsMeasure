package de.j4velin.mapsmeasure

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MeasureUiStateTest {

    private val a = LatLng(0.0, 0.0)
    private val b = LatLng(0.0, 0.001)
    private val c = LatLng(0.001, 0.001)

    @Test
    fun distanceIsTheLengthOfTheTrace() {
        assertEquals(0.0, MeasureUiState().distance, 0.0)
        assertEquals(0.0, MeasureUiState(trace = listOf(a)).distance, 0.0)
        val expected = SphericalUtil.computeDistanceBetween(a, b) + SphericalUtil.computeDistanceBetween(b, c)
        assertEquals(expected, MeasureUiState(trace = listOf(a, b, c)).distance, 1e-9)
    }

    @Test
    fun areaNeedsThreePoints() {
        assertEquals(0.0, MeasureUiState(trace = listOf(a, b)).area, 0.0)
        assertEquals(SphericalUtil.computeArea(listOf(a, b, c)), MeasureUiState(trace = listOf(a, b, c)).area, 0.0)
    }

    @Test
    fun formattedValueFollowsTypeAndUnits() {
        val state = MeasureUiState(trace = listOf(a, b, c))
        assertEquals("222.39 m", state.formattedValue(Locale.US))
        assertEquals("0.14 mi\n729.63 ft", state.copy(metric = false).formattedValue(Locale.US))
        assertEquals("6,182 m²", state.copy(type = MeasureType.AREA).formattedValue(Locale.US))
        assertEquals("0 m²", MeasureUiState(type = MeasureType.AREA).formattedValue(Locale.US))
    }

    @Test
    fun mapLayerIsReadFromTheStoredMapType() {
        // the values the settings stored before there was an enum: normal 1, hybrid 4, terrain 3
        assertEquals(MapLayer.MAP, MapLayer.fromGoogleMapType(1))
        assertEquals(MapLayer.SATELLITE, MapLayer.fromGoogleMapType(4))
        assertEquals(MapLayer.TERRAIN, MapLayer.fromGoogleMapType(3))
        assertEquals(MapLayer.MAP, MapLayer.fromGoogleMapType(42))
    }
}
