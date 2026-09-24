/*
 * Copyright 2014 Thomas Hoffmann
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
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.BadParcelableException
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.PermissionChecker
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.max

class Map : FragmentActivity(), OnMapReadyCallback {

    enum class MeasureType {
        DISTANCE, AREA
    }

    // the map to draw to
    private var googleMap: GoogleMap? = null
    private var drawerLayout: DrawerLayout? = null

    // everytime the user touches the map, an entry is added to each of these lists
    private val trace = ArrayList<LatLng>()
    private val lines = ArrayList<Polyline>()
    private val points = ArrayList<Marker?>()

    private var areaOverlay: Polygon? = null

    private var distance = 0f // in meters
    private var type = MeasureType.DISTANCE // the currently selected measure type
    private lateinit var valueTv: TextView // the view displaying the distance/area & unit

    private var marker: BitmapDescriptor? = null

    private lateinit var drawerListAdapter: DrawerListAdapter

    private var navBarOnRight = false
    private var drawerSize = 0
    private var statusbar = 0
    private var navBarHeight = 0

    // state from before the activity got recreated, applied once the map is ready
    private var stateRestored = false
    private var restoredTrace: List<LatLng>? = null
    private var restoredCamera: CameraPosition? = null

    // what to do with the location once we have the permission
    private var pendingLocationAction: ((Location?) -> Unit)? = null

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onLocationPermissionResult()
        }

    fun closeDrawer() {
        drawerLayout?.closeDrawers()
    }

    /**
     * Get the formatted string for the valueTextView.
     *
     * Depending on the measure type, the returned string shows the
     * distance of the trace or the area between them. For the area,
     * this call might be expensive as the area is computed here and not cached.
     *
     * @return the formatted text for the valueTextView
     */
    private fun getFormattedString(): String {
        if (type == MeasureType.DISTANCE) {
            return if (metric) {
                if (distance > 1000) formatter_two_dec.format((distance / 1000).toDouble()) + " km"
                else formatter_two_dec.format(max(0f, distance).toDouble()) + " m"
            } else {
                if (distance > 1609) {
                    formatter_two_dec.format((distance / 1609.344f).toDouble()) + " mi"
                } else if (distance > 30) {
                    formatter_two_dec.format((distance / 1609.344f).toDouble()) + " mi\n" +
                            formatter_two_dec.format(max(0f, distance / 0.3048f).toDouble()) + " ft"
                } else {
                    formatter_two_dec.format(max(0f, distance / 0.3048f).toDouble()) + " ft"
                }
            }
        } else {
            areaOverlay?.remove()
            val area: Double
            if (trace.size >= 3) {
                area = SphericalUtil.computeArea(trace)
                areaOverlay = googleMap?.addPolygon(
                    PolygonOptions().addAll(trace).strokeWidth(0f).fillColor(COLOR_POINT)
                )
            } else {
                area = 0.0
            }
            return if (metric) {
                if (area > 1000000) formatter_two_dec.format(max(0.0, area / 1000000.0)) + " km²"
                else formatter_no_dec.format(max(0.0, area)) + " m²"
            } else {
                if (area >= 2589989) {
                    formatter_two_dec.format(max(0.0, area / 2589988.110336)) + " mi²"
                } else {
                    formatter_no_dec.format(max(0.0, area / 0.09290304)) + " ft²"
                }
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        stateRestored = true
        try {
            metric = savedInstanceState.getBoolean("metric")
            restoredTrace = BundleCompat.getParcelableArrayList(
                savedInstanceState, "trace", LatLng::class.java
            )
            if (savedInstanceState.containsKey("position-zoom")) {
                restoredCamera = CameraPosition.fromLatLngZoom(
                    LatLng(
                        savedInstanceState.getDouble("position-lat"),
                        savedInstanceState.getDouble("position-lon")
                    ),
                    savedInstanceState.getFloat("position-zoom")
                )
            }
            savedInstanceState.getString("type")?.let { changeType(MeasureType.valueOf(it)) }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "can not restore state", e)
        }
        // the map is usually not ready yet, onMapReady applies the state then
        if (googleMap != null) applyRestoredState()
    }

    /**
     * Draws the trace and moves the camera to where they were before the activity got recreated
     */
    private fun applyRestoredState() {
        restoredTrace?.let {
            it.forEach { point -> addPoint(point) }
            restoredTrace = null
        }
        restoredCamera?.let {
            googleMap?.moveCamera(CameraUpdateFactory.newCameraPosition(it))
            restoredCamera = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // if the map never got ready, the state from last time was not applied yet
        outState.putParcelableArrayList("trace", ArrayList(restoredTrace ?: trace))
        outState.putBoolean("metric", metric)
        outState.putString("type", type.name)
        val camera = googleMap?.cameraPosition ?: restoredCamera
        if (camera != null) {
            outState.putDouble("position-lon", camera.target.longitude)
            outState.putDouble("position-lat", camera.target.latitude)
            outState.putFloat("position-zoom", camera.zoom)
        }
        super.onSaveInstanceState(outState)
    }

    /**
     * Adds a new point, calculates the new distance and draws the point and a
     * line to it
     *
     * @param p the new point
     */
    private fun addPoint(p: LatLng) {
        val map = googleMap ?: return
        if (trace.isNotEmpty()) {
            val last = trace.last()
            lines.add(
                map.addPolyline(
                    PolylineOptions().color(COLOR_LINE).width(LINE_WIDTH).add(last).add(p)
                )
            )
            distance = (distance + SphericalUtil.computeDistanceBetween(p, last)).toFloat()
        }
        points.add(drawMarker(map, p))
        trace.add(p)
        updateValueText()
    }

    /**
     * Resets the map by removing all points, lines and setting the text to 0
     */
    private fun clear() {
        googleMap?.clear()
        trace.clear()
        lines.clear()
        points.clear()
        distance = 0f
        updateValueText()
    }

    /**
     * Removes the last added point, the line to it and updates the distance
     */
    private fun removeLast() {
        if (trace.isEmpty()) return
        points.removeAt(points.lastIndex)?.remove()
        val removed = trace.removeAt(trace.lastIndex)
        if (trace.isNotEmpty()) {
            distance = (distance - SphericalUtil.computeDistanceBetween(removed, trace.last())).toFloat()
        }
        if (lines.isNotEmpty()) lines.removeAt(lines.lastIndex).remove()
        updateValueText()
    }

    /**
     * Replaces the current points on the map with the ones from the given file
     *
     * @param uri the file to read from
     */
    @Throws(IOException::class)
    fun loadTrace(uri: Uri) {
        val loaded = TraceFile.load(contentResolver, uri)
        clear()
        loaded.forEach { addPoint(it) }
        loaded.firstOrNull()?.let { moveCamera(it) }
    }

    /**
     * Searches for the given place and moves the map there
     */
    fun searchLocation(query: String) {
        lifecycleScope.launch {
            val address = findAddress(this@Map, query)
            if (address == null) {
                if (BuildConfig.DEBUG) Log.d(LOG_TAG, "no location found")
                Toast.makeText(this@Map, R.string.no_location_found, Toast.LENGTH_SHORT).show()
            } else {
                googleMap?.let {
                    it.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(address.latitude, address.longitude),
                            max(10f, it.cameraPosition.zoom)
                        )
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
        } catch (bpe: BadParcelableException) {
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "can not restore state", bpe)
        }
        init()
    }

    /**
     * Initializes everything
     */
    private fun init() {
        setContentView(R.layout.activity_map)

        formatter_no_dec.maximumFractionDigits = 0
        formatter_two_dec.maximumFractionDigits = 2

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        // use metric a the default everywhere, except in the US
        metric = prefs.getBoolean("metric", Locale.getDefault() != Locale.US)

        val topCenterOverlay = findViewById<View>(R.id.topCenterOverlay)
        val drawerLayout: DrawerLayout? = findViewById(R.id.drawer_layout)
        this.drawerLayout = drawerLayout

        val menuButton: View? = findViewById(R.id.menu)
        menuButton?.setOnClickListener { drawerLayout?.openDrawer(GravityCompat.START) }

        drawerLayout?.let {
            it.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)

            it.addDrawerListener(object : DrawerLayout.DrawerListener {
                private var menuButtonVisible = true

                override fun onDrawerStateChanged(newState: Int) {}

                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    topCenterOverlay.alpha = 1 - slideOffset
                    if (menuButtonVisible && menuButton != null && slideOffset > 0) {
                        menuButton.visibility = View.INVISIBLE
                        menuButtonVisible = false
                    }
                }

                override fun onDrawerOpened(drawerView: View) {
                    topCenterOverlay.visibility = View.INVISIBLE
                }

                override fun onDrawerClosed(drawerView: View) {
                    topCenterOverlay.visibility = View.VISIBLE
                    if (menuButton != null) {
                        menuButton.visibility = View.VISIBLE
                        menuButtonVisible = true
                    }
                }
            })
        }

        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)

        valueTv = findViewById(R.id.distance)
        updateValueText()
        valueTv.setOnClickListener {
            changeType(if (type == MeasureType.DISTANCE) MeasureType.AREA else MeasureType.DISTANCE)
        }

        val delete = findViewById<View>(R.id.delete)
        delete.setOnClickListener { removeLast() }
        delete.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setMessage(resources.getQuantityString(R.plurals.delete_all, trace.size, trace.size))
                .setPositiveButton(android.R.string.yes) { dialog, _ ->
                    clear()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.no) { dialog, _ -> dialog.dismiss() }
                .create().show()
            true
        }

        // Drawer stuff
        val drawerList = findViewById<ListView>(R.id.left_drawer)
        drawerListAdapter = DrawerListAdapter(this)
        drawerList.adapter = drawerListAdapter
        drawerList.divider = null
        drawerList.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                2 -> { // Units
                    Dialogs.getUnits(this, distance, SphericalUtil.computeArea(trace)).show()
                    closeDrawer()
                }
                3 -> changeType(MeasureType.DISTANCE)
                4 -> changeType(MeasureType.AREA)
                6 -> changeView(GoogleMap.MAP_TYPE_NORMAL)
                7 -> changeView(GoogleMap.MAP_TYPE_HYBRID)
                8 -> changeView(GoogleMap.MAP_TYPE_TERRAIN)
                10 -> { // save
                    Dialogs.getSaveNShare(this, trace).show()
                    closeDrawer()
                }
                11 -> { // more apps
                    try {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, "market://search?q=pub:j4velin".toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (anf: ActivityNotFoundException) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://play.google.com/store/apps/developer?id=j4velin".toUri()
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                12 -> { // about
                    Dialogs.getAbout(this).show()
                    closeDrawer()
                }
            }
        }

        changeType(MeasureType.DISTANCE)

        drawerSize = if (drawerLayout == null) {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, resources.displayMetrics)
                .toInt()
        } else {
            0
        }

        // the window is drawn behind the translucent system bars -> move the overlays out of their way
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusbar = bars.top
            // in landscape on phones, the navigation bar might be at the
            // right side, reducing the available display width
            navBarOnRight = bars.right > 0
            navBarHeight = if (navBarOnRight) bars.right else bars.bottom

            val lp = topCenterOverlay.layoutParams as FrameLayout.LayoutParams
            lp.setMargins(0, statusbar + 10, 0, 0)
            topCenterOverlay.layoutParams = lp

            drawerList.setPadding(0, statusbar + 10, 0, 0)
            if (navBarOnRight) {
                drawerListAdapter.setMarginBottom(0)
                menuButton?.setPadding(0, 0, 0, 0)
            } else {
                drawerListAdapter.setMarginBottom(navBarHeight)
                menuButton?.setPadding(0, 0, 0, navBarHeight)
            }

            if (googleMap != null) updateMapPadding()
            windowInsets
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        marker = BitmapDescriptorFactory.fromResource(R.drawable.marker)

        changeView(
            getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getInt("mapView", GoogleMap.MAP_TYPE_NORMAL)
        )

        map.setOnMarkerClickListener {
            addPoint(it.position)
            true
        }

        map.uiSettings.isMyLocationButtonEnabled = true
        map.setOnMyLocationButtonClickListener {
            withCurrentLocation { location ->
                if (location != null) {
                    val myLocation = LatLng(location.latitude, location.longitude)
                    val distance =
                        SphericalUtil.computeDistanceBetween(myLocation, map.cameraPosition.target)

                    // Only if the distance is less than 50cm we are on our location, add the marker
                    if (distance < 0.5) {
                        Toast.makeText(this, R.string.marker_on_current_location, Toast.LENGTH_SHORT)
                            .show()
                        addPoint(myLocation)
                    } else {
                        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "location accuracy too bad to add point")
                        moveCamera(myLocation)
                    }
                }
            }
            true
        }

        map.setOnMapClickListener { addPoint(it) }

        if (hasLocationPermission()) {
            map.isMyLocationEnabled = true
        }

        updateMapPadding()

        val data = intent.data
        if (stateRestored) {
            // recreated, e.g. after a rotation -> continue where the user was
            applyRestoredState()
        } else if (Intent.ACTION_VIEW == intent.action && data != null) {
            // opened with a csv file
            try {
                loadTrace(data)
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) Log.d(LOG_TAG, "can not load $data", e)
                Toast.makeText(
                    this,
                    getString(R.string.error, e.javaClass.simpleName + "\n" + e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            withCurrentLocation { location ->
                if (location != null && map.cameraPosition.zoom <= 5) {
                    moveCamera(LatLng(location.latitude, location.longitude))
                }
            }
        }
    }

    /**
     * Keeps the map controls out from under the system bars
     */
    private fun updateMapPadding() {
        if (navBarOnRight) {
            // in landscape on phones, the navigation bar might be at the
            // right side, reducing the available display width
            googleMap?.setPadding(drawerSize, statusbar, navBarHeight, 0)
        } else {
            googleMap?.setPadding(0, statusbar, 0, navBarHeight)
        }
    }

    /**
     * Tries to get the users current position, asking for the location permission if needed
     *
     * @param action what to do with the location, which might be null
     */
    private fun withCurrentLocation(action: (Location?) -> Unit) {
        if (hasLocationPermission()) {
            lifecycleScope.launch { action(lastLocation()) }
        } else { // no permission
            pendingLocationAction = action
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    /**
     * @return the last known location or null, if there is none
     */
    @SuppressLint("MissingPermission")
    private suspend fun lastLocation(): Location? = suspendCancellableCoroutine { continuation ->
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resume(null) }
    }

    /**
     * Moves the map view to the given position
     *
     * @param pos  the position to move to
     * @param zoom the zoom to apply
     */
    private fun moveCamera(pos: LatLng, zoom: Float = 16f) {
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, zoom))
    }

    /**
     * Change the "type" of measuring: Distance or Area
     *
     * @param newType the type to change to
     */
    private fun changeType(newType: MeasureType) {
        type = newType
        drawerListAdapter.changeType(newType)
        updateValueText()
        drawerLayout?.closeDrawers()
        if (newType != MeasureType.AREA) {
            areaOverlay?.remove()
        }
    }

    /**
     * Change between normal map, satellite hybrid and terrain view
     *
     * @param newView the new view, should be one of GoogleMap.MAP_TYPE_NORMAL,
     * GoogleMap.MAP_TYPE_HYBRID or GoogleMap.MAP_TYPE_TERRAIN
     */
    private fun changeView(newView: Int) {
        googleMap?.mapType = newView
        drawerListAdapter.changeView(newView)
        drawerLayout?.closeDrawers()
        getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putInt("mapView", newView) }
    }

    /**
     * Draws a marker at the given point.
     *
     * @param center the point where the user clicked
     * @return the drawn marker
     */
    private fun drawMarker(map: GoogleMap, center: LatLng): Marker? =
        map.addMarker(MarkerOptions().position(center).flat(true).anchor(0.5f, 0.5f).icon(marker))

    /**
     * Updates the valueTextView at the top of the screen
     */
    fun updateValueText() {
        if (::valueTv.isInitialized) valueTv.text = getFormattedString()
    }

    @SuppressLint("MissingPermission")
    private fun onLocationPermissionResult() {
        if (hasLocationPermission()) {
            pendingLocationAction?.let {
                pendingLocationAction = null
                withCurrentLocation(it)
            }
            googleMap?.isMyLocationEnabled = true
        } else {
            val savedLocation = getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("lastLocation", null)
            if (savedLocation != null && savedLocation.contains("#")) {
                val data = savedLocation.split("#")
                try {
                    if (data.size == 3 && googleMap != null) {
                        moveCamera(LatLng(data[0].toDouble(), data[1].toDouble()), data[2].toFloat())
                    }
                } catch (nfe: NumberFormatException) {
                    if (BuildConfig.DEBUG) Log.d(LOG_TAG, "invalid last location", nfe)
                }
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val drawerLayout = drawerLayout ?: return true
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawers()
        else drawerLayout.openDrawer(GravityCompat.START)
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        googleMap?.let {
            val lastPosition = it.cameraPosition
            getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                putString(
                    "lastLocation",
                    "${lastPosition.target.latitude}#${lastPosition.target.longitude}#${lastPosition.zoom}"
                )
            }
        }
    }

    /**
     * @return true, if the user granted at least approximate location access, which is
     * enough for the location button and to center the map
     */
    private fun hasLocationPermission(): Boolean =
        PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PermissionChecker.PERMISSION_GRANTED ||
                PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PermissionChecker.PERMISSION_GRANTED

    companion object {
        const val LOG_TAG = "MapsMeasure"

        private val COLOR_LINE = Color.argb(128, 0, 0, 0)
        private val COLOR_POINT = Color.argb(128, 255, 0, 0)
        private const val LINE_WIDTH = 5f

        // display in metric units
        @JvmField
        var metric = false

        @SuppressLint("ConstantLocale")
        @JvmField
        val formatter_two_dec: NumberFormat = NumberFormat.getInstance(Locale.getDefault())

        @SuppressLint("ConstantLocale")
        private val formatter_no_dec: NumberFormat = NumberFormat.getInstance(Locale.getDefault())
    }
}
