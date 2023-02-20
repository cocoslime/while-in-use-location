/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.whileinuselocation

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.android.whileinuselocation.ForegroundOnlyLocationService.Companion.ACTION_CANCEL_LOCATION_TRACKING_FROM_NOTIFICATION
import com.example.android.whileinuselocation.ForegroundOnlyLocationService.Companion.ACTION_START_FOREGROUND
import com.google.android.material.snackbar.Snackbar

private const val TAG = "MainActivity"
private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34

/**
 *  This app allows a user to receive location updates without the background permission even when
 *  the app isn't in focus. This is the preferred approach for Android.
 *
 *  It does this by creating a foreground service (tied to a Notification) when the
 *  user navigates away from the app. Because of this, it only needs foreground or "while in use"
 *  location permissions. That is, there is no need to ask for location in the background (which
 *  requires additional permissions in the manifest).
 *
 *  Note: Users have the following options in Android 11+ regarding location:
 *
 *  * Allow all the time
 *  * Allow while app is in use, i.e., while app is in foreground (new in Android 10)
 *  * Allow one time use (new in Android 11)
 *  * Not allow location at all
 *
 * It is generally recommended you only request "while in use" location permissions (location only
 * needed in the foreground), e.g., fine and coarse. If your app has an approved use case for
 * using location in the background, request that permission in context and separately from
 * fine/coarse location requests. In addition, if the user denies the request or only allows
 * "while-in-use", handle it gracefully. To see an example of background location, please review
 * {@link https://github.com/android/location-samples/tree/master/LocationUpdatesBackgroundKotlin}.
 *
 * Android 10 and higher also now requires developers to specify foreground service type in the
 * manifest (in this case, "location").
 *
 * For the feature that requires location in the foreground, this sample uses a long-running bound
 * and started service for location updates. The service is aware of foreground status of this
 * activity, which is the only bound client in this sample.
 *
 * While getting location in the foreground, if the activity ceases to be in the foreground (user
 * navigates away from the app), the service promotes itself to a foreground service and continues
 * receiving location updates.
 *
 * When the activity comes back to the foreground, the foreground service stops, and the
 * notification associated with that foreground service is removed.
 *
 * While the foreground service notification is displayed, the user has the option to launch the
 * activity from the notification. The user can also remove location updates directly from the
 * notification. This dismisses the notification and stops the service.
 */
class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var hasPermission = false

    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        initPermission()
    }

    override fun onStart() {
        super.onStart()

        if (permissionApproved()) {
            hasPermission = true
        }

        val intent = Intent(applicationContext, ForegroundOnlyLocationService::class.java)
        intent.action = ACTION_CANCEL_LOCATION_TRACKING_FROM_NOTIFICATION
        startService(intent)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        val intent = Intent(applicationContext, ForegroundOnlyLocationService::class.java)
        intent.action = ACTION_START_FOREGROUND
        startService(intent)

        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        // Updates button states if new while in use location is added to SharedPreferences.
        if (key == SharedPreferenceUtil.KEY_FOREGROUND_ENABLED) {
            sharedPreferences.getBoolean(
                SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)
        }
    }

    private fun initPermission() {
        // https://developer.android.com/training/permissions/requesting#manage-request-code-yourself
        // https://developer.android.com/training/location/permissions
        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    // Precise location access granted.
                    Log.d(TAG, "Precise location granted")
                    hasPermission = true
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    // Only approximate location access granted.
                    Log.d(TAG, "approximate location granted")
                    hasPermission = true
                }
                else -> {
                    // No location access granted.
                    showSettingSnackbar()
                }
            }
        }

        when {
            permissionApproved() -> {
                // Every permissions is already granted
                Log.d(TAG, "All permissions are granted")
                hasPermission = true
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                showSettingSnackbar()
            }
            else -> locationPermissionRequest.launch(permissions)
        }
    }

    private fun showSettingSnackbar() {
        Snackbar.make(
            this.findViewById(android.R.id.content),
            R.string.permission_rationale,
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.settings) {
                // Build intent that displays the App settings screen.
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                val uri = Uri.fromParts(
                    "package",
                    BuildConfig.APPLICATION_ID,
                    null
                )
                intent.data = uri
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            .show()
    }

    private fun permissionApproved(): Boolean {
        return permissions.any { permission ->
            ActivityCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
