/*
 * Copyright 2018 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarcns.passive.ppg

import android.Manifest.permission.CAMERA
import android.content.pm.PackageManager.FEATURE_CAMERA
import android.content.pm.PackageManager.FEATURE_CAMERA_FLASH
import org.radarbase.android.device.DeviceServiceProvider

class PhonePpgProvider : DeviceServiceProvider<PhonePpgState>() {
    override val serviceClass: Class<PhonePpgService> = PhonePpgService::class.java

    override val displayName: String
        get() = radarService!!.getString(R.string.ppg_display_name)

    override val permissionsNeeded: List<String> = listOf(CAMERA)

    override val featuresNeeded: List<String> = listOf(FEATURE_CAMERA, FEATURE_CAMERA_FLASH)

    override val sourceProducer: String = "RATE-AF"

    override val sourceModel: String = "PPG"

    override val version: String = BuildConfig.VERSION_NAME

    override val isDisplayable: Boolean = false
}
