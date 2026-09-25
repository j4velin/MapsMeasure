package de.j4velin.mapsmeasure

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnitsTest {

    private fun distance(meters: Double, metric: Boolean, locale: Locale = Locale.US) =
        Units.formatDistance(meters, metric, locale)

    private fun area(squareMeters: Double, metric: Boolean, locale: Locale = Locale.US) =
        Units.formatArea(squareMeters, metric, locale)

    @Test
    fun metricDistance() {
        assertEquals("0 m", distance(0.0, true))
        assertEquals("999.5 m", distance(999.5, true))
        assertEquals("1,000 m", distance(1000.0, true))
        assertEquals("1.5 km", distance(1500.0, true))
        assertEquals("1.23 km", distance(1234.0, true))
    }

    @Test
    fun imperialDistance() {
        assertEquals("65.62 ft", distance(20.0, false))
        // between 30 m and a mile, both
        assertEquals("0.06 mi\n328.08 ft", distance(100.0, false))
        assertEquals("1.24 mi", distance(2000.0, false))
    }

    @Test
    fun metricArea() {
        assertEquals("0 m²", area(0.0, true))
        assertEquals("5,000 m²", area(5000.4, true))
        assertEquals("2 km²", area(2_000_000.0, true))
    }

    @Test
    fun imperialArea() {
        assertEquals("10,764 ft²", area(1000.0, false))
        assertEquals("2 mi²", area(2 * 2589988.110336, false))
    }

    @Test
    fun usesTheLocale() {
        assertEquals("1,5 km", distance(1500.0, true, Locale.GERMANY))
        assertEquals("5.000 m²", area(5000.0, true, Locale.GERMANY))
        assertEquals("1.234,57", Units.twoDecimals(Locale.GERMANY).format(1234.5678))
    }
}
