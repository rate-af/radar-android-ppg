package com.vl.recordaf;

import org.radarcns.android.device.BaseDeviceState;
import org.radarcns.android.device.DeviceStatusListener;

import static org.radarcns.android.device.DeviceStatusListener.Status.CONNECTED;

public class PhonePpgState extends BaseDeviceState {
    private long recordingStarted;
    private OnActionListener actionListener;
    private OnStateChangeListener stateChangeListener;

    @Override
    public synchronized void setStatus(DeviceStatusListener.Status status) {
        if (getStatus() != CONNECTED && status == CONNECTED) {
            recordingStarted = System.currentTimeMillis();
        }
        super.setStatus(status);
    }

    public long getRecordingTime() {
        return System.currentTimeMillis() - recordingStarted;
    }

    public OnActionListener getActionListener() {
        return actionListener;
    }

    public void setActionListener(OnActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public OnStateChangeListener getStateChangeListener() {
        return stateChangeListener;
    }

    public void setStateChangeListener(OnStateChangeListener stateChangeListener) {
        this.stateChangeListener = stateChangeListener;
    }

    public interface OnActionListener {
        void startCamera();
        void stopCamera();
    }

    public interface OnStateChangeListener {
        void release();
        void acquire();
    }
}
