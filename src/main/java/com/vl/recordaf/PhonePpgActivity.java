package com.vl.recordaf;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toolbar;

import org.radarcns.android.IRadarBinder;
import org.radarcns.android.RadarApplication;
import org.radarcns.android.device.DeviceServiceProvider;

import java.util.Objects;

import static org.radarcns.android.device.DeviceStatusListener.Status.CONNECTED;
import static org.radarcns.android.device.DeviceStatusListener.Status.DISCONNECTED;
import static org.radarcns.android.device.DeviceStatusListener.Status.READY;

public class PhonePpgActivity extends Activity implements Runnable {
    private TextView mTextField;
    private PhonePpgProvider ppgProvider;
    private Handler handler;
    boolean wasConnected;
    private Button startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_ppg_activity);

        startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> {
                    PhonePpgState deviceData = getState();
                    if (deviceData == null) {
                        return;
                    }
                    PhonePpgState.OnActionListener actionListener = deviceData.getActionListener();
                    if (actionListener == null) {
                        return;
                    }
                    if (deviceData.getStatus() == DISCONNECTED || deviceData.getStatus() == READY) {
                        actionListener.startCamera();


                    } else {
                        actionListener.stopCamera();
                    }
                });

        View ml = findViewById(R.id.phonePpgFragmentLayout);
        ml.bringToFront();

        mTextField = findViewById(R.id.ppgMeasurementStatus);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.ppg_app);
        setActionBar(toolbar);
        ActionBar actionBar = Objects.requireNonNull(getActionBar());
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        wasConnected = false;

        handler = new Handler(getMainLooper());
    }

    private PhonePpgState getState() {
        if (ppgProvider == null || !ppgProvider.getConnection().hasService()) {
            return null;
        }
        return ppgProvider.getConnection().getDeviceData();
    }

    private final ServiceConnection radarServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            IRadarBinder radarService = (IRadarBinder) service;
            ppgProvider = null;
            for (DeviceServiceProvider provider : radarService.getConnections()) {
                if (provider instanceof PhonePpgProvider) {
                    ppgProvider = (PhonePpgProvider) provider;
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            ppgProvider = null;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();

        bindService(new Intent(this, ((RadarApplication)getApplication()).getRadarService()), radarServiceConnection, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(this, 50);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(radarServiceConnection);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(this, ((RadarApplication)getApplication()).getMainActivity())
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public void run() {
        PhonePpgState state = getState();
        if (state != null && state.getStatus() == CONNECTED) {
            mTextField.setText("Recording " + state.getRecordingTime() / 1000 + " seconds...");
            startButton.setText(R.string.ppg_stop);
            wasConnected = true;
        } else if (wasConnected) {
            mTextField.setText(R.string.ppg_done);
            startButton.setText(R.string.start);
        } else {
            mTextField.setText(R.string.ppg_not_started);
            startButton.setText(R.string.start);
        }
        handler.postDelayed(this, 50);
    }
}
