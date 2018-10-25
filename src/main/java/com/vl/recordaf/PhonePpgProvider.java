package com.vl.recordaf;

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
    private static final String PREFIX = "com.vl.recordaf.PhonePpgProvider.";

    private static final String PPG_MEASUREMENT_TIME_NAME = "phone_ppg_measurement_seconds";
    private static final String PPG_MEASUREMENT_WIDTH_NAME = "phone_ppg_measurement_width";
    private static final String PPG_MEASUREMENT_HEIGHT_NAME = "phone_ppg_measurement_height";

    public static final String PPG_MEASUREMENT_TIME_KEY = PREFIX + PPG_MEASUREMENT_TIME_NAME;
    public static final String PPG_MEASUREMENT_WIDTH_KEY = PREFIX + PPG_MEASUREMENT_WIDTH_NAME;
    public static final String PPG_MEASUREMENT_HEIGHT_KEY = PREFIX + PPG_MEASUREMENT_HEIGHT_NAME;

    private static final int PPG_MEASUREMENT_TIME_DEFAULT = 60;
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
