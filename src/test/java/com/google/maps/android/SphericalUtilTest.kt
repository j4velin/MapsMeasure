package com.google.maps.android

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class SphericalUtilTest {

    private val earthRadius = 6371000.0

    @Test
    fun distanceBetweenSamePointIsZero() {
        val p = LatLng(52.52, 13.405)
        assertEquals(0.0, SphericalUtil.computeDistanceBetween(p, p), 1e-9)
    }

    @Test
    fun distanceAlongEquatorAndMeridian() {
        // a quarter of a great circle
        val quarter = PI / 2 * earthRadius
        assertEquals(quarter, SphericalUtil.computeDistanceBetween(LatLng(0.0, 0.0), LatLng(0.0, 90.0)), 1e-6)
        assertEquals(quarter, SphericalUtil.computeDistanceBetween(LatLng(0.0, 0.0), LatLng(90.0, 0.0)), 1e-6)
        // one degree
        assertEquals(PI / 180 * earthRadius, SphericalUtil.computeDistanceBetween(LatLng(0.0, 0.0), LatLng(0.0, 1.0)), 1e-6)
    }

    @Test
    fun distanceIsSymmetric() {
        val berlin = LatLng(52.5200, 13.4050)
        val paris = LatLng(48.8566, 2.3522)
        val there = SphericalUtil.computeDistanceBetween(berlin, paris)
        assertEquals(there, SphericalUtil.computeDistanceBetween(paris, berlin), 1e-9)
        // about 878 km
        assertEquals(877_500.0, there, 1_000.0)
    }

    @Test
    fun areaNeedsThreePoints() {
        assertEquals(0.0, SphericalUtil.computeArea(emptyList()), 0.0)
        assertEquals(0.0, SphericalUtil.computeArea(listOf(LatLng(0.0, 0.0))), 0.0)
        assertEquals(0.0, SphericalUtil.computeArea(listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0))), 0.0)
    }

    @Test
    fun areaOfOctant() {
        // the triangle between the equator, the prime meridian and 90° east is 1/8 of the sphere
        val octant = listOf(LatLng(0.0, 0.0), LatLng(0.0, 90.0), LatLng(90.0, 0.0))
        assertEquals(4 * PI * earthRadius * earthRadius / 8, SphericalUtil.computeArea(octant), 1.0)
    }

    @Test
    fun areaDoesNotDependOnDirection() {
        val square = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.001), LatLng(0.001, 0.001), LatLng(0.001, 0.0))
        val area = SphericalUtil.computeArea(square)
        assertEquals(area, SphericalUtil.computeArea(square.reversed()), 1e-6)
        // about 111.2 m x 111.2 m
        assertEquals(12_364.0, area, 5.0)
    }
}
