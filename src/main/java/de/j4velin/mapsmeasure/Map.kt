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
import android.content.Intent
import android.graphics.Color
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
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * The measure screen. All state lives in the [MeasureViewModel]; this activity draws it and
 * forwards the user's input.
 */
class Map : FragmentActivity(), OnMapReadyCallback {

    private lateinit var viewModel: MeasureViewModel

    private var drawerLayout: DrawerLayout? = null
    private lateinit var valueTv: TextView // the view displaying the distance/area & unit
    private lateinit var drawerListAdapter: DrawerListAdapter
    private var renderedType: MeasureType? = null
    private var renderedMapType: Int? = null

    // what is currently drawn on the map
    private var drawnTrace: List<LatLng> = emptyList()
    private val lines = ArrayList<Polyline>() // lines[i] connects drawnTrace[i] and drawnTrace[i + 1]
    private val points = ArrayList<Marker?>() // points[i] marks drawnTrace[i]
    private var areaOverlay: Polygon? = null
    private var drawnArea: List<LatLng>? = null
    private var marker: BitmapDescriptor? = null

    private var navBarOnRight = false
    private var drawerSize = 0
    private var statusbar = 0
    private var navBarHeight = 0

    private var googleMap: GoogleMap? = null

    // what to do once we have the location permission
    private var pendingLocationAction: (() -> Unit)? = null

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onLocationPermissionResult()
        }

    fun closeDrawer() {
        drawerLayout?.closeDrawers()
    }

    /**
     * Replaces the current points with the ones from the given file
     */
    fun loadTrace(uri: Uri) = viewModel.loadTrace(uri)

    /**
     * Searches for the given place and moves the map there
     */
    fun searchLocation(query: String) = viewModel.search(query)

    fun setMetric(metric: Boolean) = viewModel.setMetric(metric)

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
        } catch (bpe: BadParcelableException) {
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "can not restore state", bpe)
        }
        viewModel = ViewModelProvider(this, MeasureViewModel.Factory)[MeasureViewModel::class.java]
        init()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    /**
     * Initializes everything
     */
    private fun init() {
        setContentView(R.layout.activity_map)

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
        valueTv.setOnClickListener {
            viewModel.toggleType()
            closeDrawer()
        }

        val delete = findViewById<View>(R.id.delete)
        delete.setOnClickListener { viewModel.removeLastPoint() }
        delete.setOnLongClickListener {
            val count = viewModel.uiState.value.trace.size
            AlertDialog.Builder(this)
                .setMessage(resources.getQuantityString(R.plurals.delete_all, count, count))
                .setPositiveButton(android.R.string.yes) { dialog, _ ->
                    viewModel.clear()
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
            val state = viewModel.uiState.value
            when (position) {
                2 -> { // Units
                    Dialogs.getUnits(this, state.metric, state.distance, state.area).show()
                    closeDrawer()
                }
                3 -> {
                    viewModel.setType(MeasureType.DISTANCE)
                    closeDrawer()
                }
                4 -> {
                    viewModel.setType(MeasureType.AREA)
                    closeDrawer()
                }
                6, 7, 8 -> {
                    viewModel.setMapType(MAP_TYPE_AT_POSITION.getValue(position))
                    closeDrawer()
                }
                10 -> { // save
                    Dialogs.getSaveNShare(this, state.trace).show()
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

            updateMapPadding()
            windowInsets
        }
    }

    /**
     * Shows the state in the views outside of the map
     */
    private fun render(state: MeasureUiState) {
        valueTv.text = state.formattedValue()
        if (state.type != renderedType) {
            drawerListAdapter.changeType(state.type)
            renderedType = state.type
        }
        if (state.mapType != renderedMapType) {
            drawerListAdapter.changeView(state.mapType)
            renderedMapType = state.mapType
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        marker = BitmapDescriptorFactory.fromResource(R.drawable.marker)

        // back where it was before the activity got recreated
        viewModel.camera?.let { map.moveCamera(CameraUpdateFactory.newCameraPosition(it)) }

        map.setOnMarkerClickListener {
            viewModel.addPoint(it.position)
            true
        }
        map.setOnMapClickListener { viewModel.addPoint(it) }
        map.setOnCameraIdleListener { viewModel.onCameraIdle(map.cameraPosition) }

        map.uiSettings.isMyLocationButtonEnabled = true
        map.setOnMyLocationButtonClickListener {
            withLocationPermission { viewModel.onMyLocationButton() }
            true
        }
        if (hasLocationPermission()) {
            map.isMyLocationEnabled = true
        }

        updateMapPadding()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { draw(map, it) } }
                viewModel.events.collect { handle(map, it) }
            }
        }

        if (viewModel.consumeFirstStart()) {
            val data = intent.data
            if (Intent.ACTION_VIEW == intent.action && data != null) {
                // opened with a csv file
                viewModel.loadTrace(data)
            } else {
                withLocationPermission { viewModel.centerOnCurrentLocation() }
            }
        }
    }

    /**
     * Shows the state on the map. Only the points and lines which changed are redrawn.
     */
    private fun draw(map: GoogleMap, state: MeasureUiState) {
        if (map.mapType != state.mapType) map.mapType = state.mapType

        val trace = state.trace
        if (trace != drawnTrace) {
            // the points both traces start with stay
            var keep = 0
            val common = min(trace.size, drawnTrace.size)
            while (keep < common && trace[keep] == drawnTrace[keep]) keep++

            while (points.size > keep) points.removeAt(points.lastIndex)?.remove()
            while (lines.size > max(keep - 1, 0)) lines.removeAt(lines.lastIndex).remove()

            for (i in keep until trace.size) {
                if (i > 0) {
                    lines.add(
                        map.addPolyline(
                            PolylineOptions().color(COLOR_LINE).width(LINE_WIDTH)
                                .add(trace[i - 1]).add(trace[i])
                        )
                    )
                }
                points.add(
                    map.addMarker(
                        MarkerOptions().position(trace[i]).flat(true).anchor(0.5f, 0.5f).icon(marker)
                    )
                )
            }
            drawnTrace = trace
        }

        val area = if (state.type == MeasureType.AREA && trace.size >= 3) trace else null
        if (area != drawnArea) {
            areaOverlay?.remove()
            areaOverlay = area?.let {
                map.addPolygon(PolygonOptions().addAll(it).strokeWidth(0f).fillColor(COLOR_POINT))
            }
            drawnArea = area
        }
    }

    private fun handle(map: GoogleMap, event: MeasureEvent) {
        when (event) {
            is MeasureEvent.MoveCamera -> {
                val update = CameraUpdateFactory.newLatLngZoom(event.target, event.zoom)
                if (event.animate) map.animateCamera(update) else map.moveCamera(update)
            }
            is MeasureEvent.Message ->
                Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
            is MeasureEvent.Error ->
                Toast.makeText(
                    this,
                    getString(
                        R.string.error,
                        event.exception.javaClass.simpleName + "\n" + event.exception.message
                    ),
                    Toast.LENGTH_LONG
                ).show()
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
     * Runs the given action now if we have the location permission, or asks for it first
     */
    private fun withLocationPermission(action: () -> Unit) {
        if (hasLocationPermission()) {
            action()
        } else {
            pendingLocationAction = action
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun onLocationPermissionResult() {
        if (hasLocationPermission()) {
            pendingLocationAction?.invoke()
            googleMap?.isMyLocationEnabled = true
        } else {
            viewModel.moveToLastPosition()
        }
        pendingLocationAction = null
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val drawerLayout = drawerLayout ?: return true
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawers()
        else drawerLayout.openDrawer(GravityCompat.START)
        return false
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

        private val MAP_TYPE_AT_POSITION = mapOf(
            6 to GoogleMap.MAP_TYPE_NORMAL,
            7 to GoogleMap.MAP_TYPE_HYBRID,
            8 to GoogleMap.MAP_TYPE_TERRAIN
        )
    }
}
