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

package org.radarbase.passive.phone.ppg;

import android.os.Bundle;
import android.support.annotation.NonNull;

import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static android.Manifest.permission.CAMERA;
import static android.content.pm.PackageManager.FEATURE_CAMERA;
import static android.content.pm.PackageManager.FEATURE_CAMERA_FLASH;

@SuppressWarnings("unused")
public class PhonePpgProvider extends DeviceServiceProvider<PhonePpgState> {
    private static final String PREFIX = "org.radarbase.passive.phone.ppg.PhonePpgProvider.";

    public static final String PPG_MEASUREMENT_TIME_NAME = "phone_ppg_measurement_seconds";
    private static final String PPG_MEASUREMENT_WIDTH_NAME = "phone_ppg_measurement_width";
    private static final String PPG_MEASUREMENT_HEIGHT_NAME = "phone_ppg_measurement_height";

    public static final String PPG_MEASUREMENT_TIME_KEY = PREFIX + PPG_MEASUREMENT_TIME_NAME;
    public static final String PPG_MEASUREMENT_WIDTH_KEY = PREFIX + PPG_MEASUREMENT_WIDTH_NAME;
    public static final String PPG_MEASUREMENT_HEIGHT_KEY = PREFIX + PPG_MEASUREMENT_HEIGHT_NAME;

    public static final int PPG_MEASUREMENT_TIME_DEFAULT = 60;
    private static final int PPG_MEASUREMENT_WIDTH_DEFAULT = 200;
    private static final int PPG_MEASUREMENT_HEIGHT_DEFAULT = 200;

    @Override
    public Class<PhonePpgService> getServiceClass() {
        return PhonePpgService.class;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @NonNull
    @Override
    public List<String> needsPermissions() {
        return Collections.singletonList(CAMERA);
    }

    @NonNull
    @Override
    public List<String> needsFeatures() {
        return Arrays.asList(FEATURE_CAMERA, FEATURE_CAMERA_FLASH);
    }

    @Override
    protected void configure(Bundle bundle) {
        super.configure(bundle);
        bundle.putInt(PPG_MEASUREMENT_TIME_KEY,
                getConfig().getInt(PPG_MEASUREMENT_TIME_NAME, PPG_MEASUREMENT_TIME_DEFAULT));
        bundle.putInt(PPG_MEASUREMENT_WIDTH_KEY,
                getConfig().getInt(PPG_MEASUREMENT_WIDTH_NAME, PPG_MEASUREMENT_WIDTH_DEFAULT));
        bundle.putInt(PPG_MEASUREMENT_HEIGHT_KEY,
                getConfig().getInt(PPG_MEASUREMENT_HEIGHT_NAME, PPG_MEASUREMENT_HEIGHT_DEFAULT));
    }

    @NonNull
    @Override
    public String getDeviceProducer() {
        return "RATE-AF";
    }

    @NonNull
    @Override
    public String getDeviceModel() {
        return "PPG";
    }

    @NonNull
    @Override
    public String getVersion() {
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public boolean isDisplayable() {
        return false;
    }
}
