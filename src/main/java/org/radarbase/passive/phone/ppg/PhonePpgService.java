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
import android.util.Size;

import org.radarcns.android.device.DeviceService;

import static org.radarbase.passive.phone.ppg.PhonePpgProvider.PPG_MEASUREMENT_HEIGHT_KEY;
import static org.radarbase.passive.phone.ppg.PhonePpgProvider.PPG_MEASUREMENT_TIME_KEY;
import static org.radarbase.passive.phone.ppg.PhonePpgProvider.PPG_MEASUREMENT_WIDTH_KEY;

public class PhonePpgService extends DeviceService<PhonePpgState> {
    private int measurementTime;
    private Size measurementDimensions;

    @Override
    protected PhonePpgManager createDeviceManager() {
        return new PhonePpgManager(this);
    }

    @Override
    protected PhonePpgState getDefaultState() {
        return new PhonePpgState();
    }

    @Override
    protected void onInvocation(@NonNull Bundle bundle) {
        super.onInvocation(bundle);
        measurementTime = bundle.getInt(PPG_MEASUREMENT_TIME_KEY);
        measurementDimensions = new Size(
                bundle.getInt(PPG_MEASUREMENT_WIDTH_KEY),
                bundle.getInt(PPG_MEASUREMENT_HEIGHT_KEY));
        PhonePpgManager deviceManager = (PhonePpgManager) getDeviceManager();
        if (deviceManager != null) {
            deviceManager.configure(measurementTime, measurementDimensions);
        }
    }

    public int getMeasurementTime() {
        return measurementTime;
    }

    public Size getMeasurementDimensions() {
        return measurementDimensions;
    }
}
