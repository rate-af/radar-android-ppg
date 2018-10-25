package com.vl.recordaf;

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
