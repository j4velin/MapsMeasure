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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * What needs the location permission. An enum instead of a lambda, so a pending action survives
 * when the screen gets recreated while the system asks for the permission, e.g. on rotation.
 */
internal enum class LocationAction { CENTER_ON_START, MY_LOCATION_BUTTON }

/**
 * @param granted whether the app may use the location, e.g. to show it on the map
 * @param request runs the action right away if the permission is granted, otherwise asks for it
 *                first
 */
internal class LocationPermission(val granted: Boolean, val request: (LocationAction) -> Unit)

/**
 * @param onGranted called with the requested action once the permission is granted
 * @param onDenied  called if the user does not grant the permission
 */
@Composable
internal fun rememberLocationPermission(
    onGranted: (LocationAction) -> Unit,
    onDenied: () -> Unit,
): LocationPermission {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasLocationPermission()) }
    var pending by rememberSaveable { mutableStateOf<LocationAction?>(null) }
    val currentOnGranted by rememberUpdatedState(onGranted)
    val currentOnDenied by rememberUpdatedState(onDenied)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            granted = context.hasLocationPermission()
            val action = pending
            pending = null
            if (!granted) currentOnDenied()
            else if (action != null) currentOnGranted(action)
        }
    return LocationPermission(granted) { action ->
        if (context.hasLocationPermission()) {
            granted = true
            currentOnGranted(action)
        } else {
            pending = action
            launcher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
