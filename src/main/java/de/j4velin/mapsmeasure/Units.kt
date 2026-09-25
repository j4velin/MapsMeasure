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

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

/**
 * Formats distances and areas in metric or imperial units
 */
object Units {

    @JvmStatic
    @JvmOverloads
    fun twoDecimals(locale: Locale = Locale.getDefault()): NumberFormat =
        NumberFormat.getInstance(locale).apply { maximumFractionDigits = 2 }

    private fun noDecimals(locale: Locale): NumberFormat =
        NumberFormat.getInstance(locale).apply { maximumFractionDigits = 0 }

    /**
     * @param distance the distance in meters
     */
    fun formatDistance(distance: Double, metric: Boolean, locale: Locale = Locale.getDefault()): String {
        val twoDec = twoDecimals(locale)
        return if (metric) {
            if (distance > 1000) twoDec.format(distance / 1000) + " km"
            else twoDec.format(max(0.0, distance)) + " m"
        } else {
            if (distance > 1609) {
                twoDec.format(distance / 1609.344) + " mi"
            } else if (distance > 30) {
                twoDec.format(distance / 1609.344) + " mi\n" +
                        twoDec.format(max(0.0, distance / 0.3048)) + " ft"
            } else {
                twoDec.format(max(0.0, distance / 0.3048)) + " ft"
            }
        }
    }

    /**
     * @param area the area in square meters
     */
    fun formatArea(area: Double, metric: Boolean, locale: Locale = Locale.getDefault()): String =
        if (metric) {
            if (area > 1000000) twoDecimals(locale).format(max(0.0, area / 1000000)) + " km²"
            else noDecimals(locale).format(max(0.0, area)) + " m²"
        } else {
            if (area >= 2589989) {
                twoDecimals(locale).format(max(0.0, area / 2589988.110336)) + " mi²"
            } else {
                noDecimals(locale).format(max(0.0, area / 0.09290304)) + " ft²"
            }
        }
}
