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

package org.radarbase.passive.ppg;

import android.os.SystemClock;

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
            recordingStarted = SystemClock.elapsedRealtime();
        }
        super.setStatus(status);
    }

    public long getRecordingTime() {
        return SystemClock.elapsedRealtime() - recordingStarted;
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
