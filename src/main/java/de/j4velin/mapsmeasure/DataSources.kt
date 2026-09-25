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

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * The settings the user chose and where the map was last
 */
interface Settings {
    /** true for metric units, false for imperial ones */
    var metric: Boolean

    var mapLayer: MapLayer

    /** where the map was when it last stopped moving, or null if it was never shown */
    var lastCamera: CameraPosition?
}

/**
 * Finds the current location of the device and places by their name
 */
interface Locator {
    /**
     * Needs the location permission
     *
     * @return the current location or null, if it is not known
     */
    suspend fun currentLocation(): LatLng?

    /**
     * @return the place best matching the given name or null, if nothing was found
     */
    suspend fun findPlace(name: String): LatLng?
}

/**
 * Reads and writes trace files
 */
interface TraceStorage {
    @Throws(IOException::class)
    suspend fun load(uri: Uri): List<LatLng>

    @Throws(IOException::class)
    suspend fun save(uri: Uri, trace: List<LatLng>)

    /**
     * Writes the trace to a temporary file
     *
     * @return a content uri other apps can read the file with
     */
    @Throws(IOException::class)
    suspend fun saveForSharing(trace: List<LatLng>): Uri

    /**
     * Traces which app versions before 2.0 saved in the app's own folders, newest first
     */
    suspend fun oldFiles(): List<File>

    suspend fun delete(file: File)
}

class PreferenceSettings(context: Context) : Settings {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override var metric: Boolean
        // use metric as the default everywhere, except in the US
        get() = prefs.getBoolean("metric", Locale.getDefault() != Locale.US)
        set(value) = prefs.edit { putBoolean("metric", value) }

    override var mapLayer: MapLayer
        get() = MapLayer.fromGoogleMapType(prefs.getInt("mapView", GoogleMap.MAP_TYPE_NORMAL))
        set(value) = prefs.edit { putInt("mapView", value.googleMapType) }

    // stored as "latitude#longitude#zoom"
    override var lastCamera: CameraPosition?
        get() {
            val data = prefs.getString("lastLocation", null)?.split("#") ?: return null
            if (data.size != 3) return null
            val lat = data[0].toDoubleOrNull() ?: return null
            val lng = data[1].toDoubleOrNull() ?: return null
            val zoom = data[2].toFloatOrNull() ?: return null
            return CameraPosition.fromLatLngZoom(LatLng(lat, lng), zoom)
        }
        set(value) = prefs.edit {
            if (value == null) remove("lastLocation")
            else putString("lastLocation", "${value.target.latitude}#${value.target.longitude}#${value.zoom}")
        }
}

class PlayServicesLocator(context: Context) : Locator {
    private val context = context.applicationContext

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LatLng? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = try {
            // the last location is null e.g. right after a reboot, so ask for a new one then
            client.lastLocation.await() ?: CancellationTokenSource().let {
                // stops the request if the coroutine is cancelled, does nothing once it completed
                try {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, it.token).await()
                } finally {
                    it.cancel()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "can not get location", e)
            null
        }
        return location?.let { LatLng(it.latitude, it.longitude) }
    }

    override suspend fun findPlace(name: String): LatLng? = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION") // the listener variant needs API 33
            Geocoder(context).getFromLocationName(name, 1)?.firstOrNull()
                ?.let { LatLng(it.latitude, it.longitude) }
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "geocoder failed", e)
            null
        }
    }
}

class FileTraceStorage(context: Context) : TraceStorage {
    private val context = context.applicationContext

    override suspend fun load(uri: Uri) =
        withContext(Dispatchers.IO) { TraceFile.load(context.contentResolver, uri) }

    override suspend fun save(uri: Uri, trace: List<LatLng>) =
        withContext(Dispatchers.IO) { TraceFile.save(context.contentResolver, uri, trace) }

    override suspend fun saveForSharing(trace: List<LatLng>): Uri {
        val file = File(context.cacheDir, "MapsMeasure.csv")
        withContext(Dispatchers.IO) { TraceFile.save(file, trace) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    override suspend fun oldFiles(): List<File> = withContext(Dispatchers.IO) {
        listOfNotNull(context.getExternalFilesDir(null), context.getDir("traces", Context.MODE_PRIVATE))
            .flatMap { it.listFiles()?.toList() ?: emptyList() }
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
    }

    override suspend fun delete(file: File) {
        withContext(Dispatchers.IO) { file.delete() }
    }
}
