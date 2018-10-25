package com.vl.recordaf;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Size;

import org.radarcns.android.device.DeviceService;

import static com.vl.recordaf.PhonePpgProvider.PPG_MEASUREMENT_HEIGHT_KEY;
import static com.vl.recordaf.PhonePpgProvider.PPG_MEASUREMENT_TIME_KEY;
import static com.vl.recordaf.PhonePpgProvider.PPG_MEASUREMENT_WIDTH_KEY;

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
