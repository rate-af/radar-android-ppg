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

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.radarbase.android.IRadarBinder
import org.radarbase.android.RadarApplication
import org.radarbase.android.device.DeviceStatusListener.Status.*
import org.radarcns.passive.ppg.PhonePpgService.Companion.PPG_MEASUREMENT_TIME_DEFAULT
import org.radarcns.passive.ppg.PhonePpgService.Companion.PPG_MEASUREMENT_TIME_NAME
import java.util.*

class PhonePpgActivity : AppCompatActivity(), Runnable {
    private var mTextField: TextView? = null
    private var ppgProvider: PhonePpgProvider? = null
    private var handler: Handler? = null
    internal var wasConnected: Boolean = false
    private var startButton: Button? = null

    private val state: PhonePpgState?
        get() = if (ppgProvider == null || !ppgProvider!!.connection.hasService()) {
            null
        } else ppgProvider!!.connection.deviceData

    private val radarServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val radarService = service as IRadarBinder
            ppgProvider = null
            for (provider in radarService.connections) {
                if (provider is PhonePpgProvider) {
                    ppgProvider = provider

                    val state = state
                    if (state != null) {
                        val listener = state.stateChangeListener
                        listener?.acquire()
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            val state = state
            if (state != null) {
                val listener = state.stateChangeListener
                listener?.release()
            }

            ppgProvider = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.phone_ppg_activity)

        startButton = findViewById(R.id.startButton)
        startButton!!.setOnClickListener { v ->
            val deviceData = state ?: return@setOnClickListener
            val actionListener = deviceData.actionListener ?: return@setOnClickListener
            if (deviceData.status == DISCONNECTED || deviceData.status == READY) {
                actionListener.startCamera()
            } else {
                actionListener.stopCamera()
            }
        }

        val ml = findViewById<View>(R.id.phonePpgFragmentLayout)
        ml.bringToFront()

        val config = (application as RadarApplication).configuration
        this.findViewById<TextView>(R.id.ppgMainDescription).text = getString(R.string.ppgMainDescription,
                config.getInt(PPG_MEASUREMENT_TIME_NAME, PPG_MEASUREMENT_TIME_DEFAULT))
        mTextField = findViewById(R.id.ppgMeasurementStatus)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitle(R.string.ppg_app)
        setSupportActionBar(toolbar)
        val actionBar = Objects.requireNonNull<ActionBar>(supportActionBar)
        actionBar.setDisplayShowHomeEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        wasConnected = false

        handler = Handler(mainLooper)
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, (application as RadarApplication).radarService), radarServiceConnection, 0)
    }

    override fun onResume() {
        super.onResume()
        handler!!.postDelayed(this, 50)
    }

    override fun onPause() {
        super.onPause()
        handler!!.removeCallbacks(this)
    }

    override fun onStop() {
        super.onStop()
        unbindService(radarServiceConnection)
    }

    override fun onNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onBackPressed() {
        startActivity(Intent(this, (application as RadarApplication).mainActivity)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        overridePendingTransition(0, 0)
        finish()
    }

    override fun run() {
        val state = state
        if (state != null && state.status == CONNECTED) {
            mTextField!!.text = getString(R.string.ppg_recording_seconds,
                    (state.recordingTime / 1000).toInt())
            startButton!!.setText(R.string.ppg_stop)
            wasConnected = true
        } else if (wasConnected) {
            mTextField!!.setText(R.string.ppg_done)
            startButton!!.setText(R.string.start)
        } else {
            mTextField!!.setText(R.string.ppg_not_started)
            startButton!!.setText(R.string.start)
        }
        handler!!.postDelayed(this, 50)
    }
}
