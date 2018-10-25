package com.vl.recordaf;

import org.radarcns.android.device.DeviceManager;
import org.radarcns.android.device.DeviceService;

public class PhonePpgService extends DeviceService<PhonePpgState> {
    @Override
    protected DeviceManager<PhonePpgState> createDeviceManager() {
        return new PhonePpgManager(this);
    }

    @Override
    protected PhonePpgState getDefaultState() {
        return new PhonePpgState();
    }
}
