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

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import java.util.Locale

enum class MeasureType {
    DISTANCE, AREA
}

/**
 * The map views the user can choose from
 *
 * @param googleMapType the matching GoogleMap.MAP_TYPE_ constant, which the settings store
 */
enum class MapLayer(val googleMapType: Int) {
    MAP(GoogleMap.MAP_TYPE_NORMAL),
    SATELLITE(GoogleMap.MAP_TYPE_HYBRID),
    TERRAIN(GoogleMap.MAP_TYPE_TERRAIN);

    companion object {
        fun fromGoogleMapType(type: Int) = entries.firstOrNull { it.googleMapType == type } ?: MAP
    }
}

/**
 * Everything the measure screen shows
 *
 * @param trace    the points the user added, in order
 * @param type     whether the distance along the trace or the area inside it is measured
 * @param metric   true to show metric units, false for imperial ones
 * @param mapLayer the map view the user chose
 */
data class MeasureUiState(
    val trace: List<LatLng> = emptyList(),
    val type: MeasureType = MeasureType.DISTANCE,
    val metric: Boolean = true,
    val mapLayer: MapLayer = MapLayer.MAP,
) {
    /** the length of the trace in meters */
    val distance: Double
        get() = trace.zipWithNext { a, b -> SphericalUtil.computeDistanceBetween(a, b) }.sum()

    /** the area inside the trace in square meters */
    val area: Double
        get() = SphericalUtil.computeArea(trace)

    /** the distance or area, depending on the type, formatted in the selected units */
    fun formattedValue(locale: Locale = Locale.getDefault()): String =
        when (type) {
            MeasureType.DISTANCE -> Units.formatDistance(distance, metric, locale)
            MeasureType.AREA -> Units.formatArea(area, metric, locale)
        }
}
