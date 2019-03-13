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

import android.content.Context
import android.hardware.camera2.*
import android.hardware.camera2.CameraCharacteristics.LENS_FACING
import android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
import android.hardware.camera2.CameraMetadata.LENS_FACING_BACK
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.renderscript.Allocation
import android.util.Size
import android.view.Surface
import org.radarbase.android.data.DataCache
import org.radarbase.android.device.AbstractDeviceManager
import org.radarbase.android.device.DeviceStatusListener
import org.radarbase.android.device.DeviceStatusListener.Status.*
import org.radarcns.kafka.ObservationKey
import org.radarcns.passive.ppg.RenderContext.RENDER_CONTEXT_RELEASER
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * AppSource manager initialization. After initialization, be sure to call
 * [.setName].
 *
 * @param service service that the manager is started by
 */
class PhonePpgManager(service: PhonePpgService) : AbstractDeviceManager<PhonePpgService, PhonePpgState>(service), PhonePpgState.OnActionListener {
    private val ppgTopic: DataCache<ObservationKey, PhoneCameraPpg> = createCache("android_phone_ppg", PhoneCameraPpg::class.java)
    private val cameraManager: CameraManager? = service.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
    private val mHandlerThread: HandlerThread
    private val mProcessorThread: HandlerThread
    @get:Synchronized
    private var preferredDimensions: Size? = null
    private var mHandler: Handler? = null
    private var mCameraDevice: CameraDevice? = null
    private var mPreviewSession: CameraCaptureSession? = null
    private var doStop: Boolean = false
    private var mProcessor: Handler? = null
    private val cameraOpenCloseLock = Semaphore(1)
    private var mRenderContext: RenderContext? = null
    @get:Synchronized
    private var measurementTime: Long = 0

    init {
        name = Build.MODEL + " PPG"

        if (cameraManager == null) {
            updateStatus(DISCONNECTED)
        }

        updateStatus(DeviceStatusListener.Status.READY)

        state.stateChangeListener = object : PhonePpgState.OnStateChangeListener {
            override fun release() {
                RENDER_CONTEXT_RELEASER.setShouldRelease(true)
            }

            override fun acquire() {
                RENDER_CONTEXT_RELEASER.setShouldRelease(false)
            }
        }

        mHandlerThread = HandlerThread("PPG")
        mProcessorThread = HandlerThread("PPG processing")
    }

    override fun start(acceptableIds: Set<String>) {
        register()
        state.actionListener = this
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)
        mProcessorThread.start()
        mProcessor = Handler(mProcessorThread.looper)
    }


    private fun openCamera(): Boolean {
        try {
            // Do not open the camera if the camera open operation is still in progress
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                logger.error("Failed to acquire camera open lock")
                return false
            }
            val cameraId = backFacingCamera(cameraManager!!)

            if (cameraId == null) {
                logger.error("Cannot get back-facing camera.")
                updateStatus(DISCONNECTED)
                return false
            }

            val videoSize = getImageSize(cameraId)
            if (videoSize == null) {
                logger.error("Cannot determine PPG image size.")
                updateStatus(DISCONNECTED)
                return false
            }

            mRenderContext = RenderContext(service, videoSize)
            mRenderContext!!.setImageHandler({ time, rgba -> this.updatePreview(time, rgba) }, mProcessor)

            updateStatus(CONNECTING)

            logger.debug("Opening camera")
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    mCameraDevice = camera
                    startPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    mCameraDevice = null
                    updateStatus(DISCONNECTED)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraOpenCloseLock.release()
                    mCameraDevice = null
                    updateStatus(DISCONNECTED)
                }
            }, mHandler)

        } catch (e: CameraAccessException) {
            logger.error("Cannot access the camera.", e)
            cameraOpenCloseLock.release()
            return false
        } catch (e: SecurityException) {
            logger.error("No access to camera device", e)
            cameraOpenCloseLock.release()
            return false
        } catch (e: InterruptedException) {
            logger.error("Could not acquire lock to camera", e)
            return false
        }

        return true
    }

    /** Start the preview session. This should only be called once the camera is open.  */
    private fun startPreview() {
        if (mCameraDevice == null) {
            return
        }
        try {
            logger.debug("Starting capture session")
            mCameraDevice!!.createCaptureSession(listOf<Surface>(mRenderContext!!.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            if (mCameraDevice == null) {
                                return
                            }
                            mPreviewSession = cameraCaptureSession
                            updateStatus(CONNECTED)
                            logger.info("Started PPG capture session")

                            try {
                                // Make a capture request, sending images to the RenderContext and enabling the torch
                                val requestBuilder = mCameraDevice!!.createCaptureRequest(TEMPLATE_PREVIEW)
                                requestBuilder.addTarget(mRenderContext!!.surface)
                                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                                val captureRequest = requestBuilder.build()

                                mPreviewSession!!.setRepeatingRequest(captureRequest, object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                                        logger.debug("Completed a capture")
                                        pollDisconnect()
                                    }

                                    override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                                        pollDisconnect()
                                    }
                                }, mHandler)
                            } catch (e: IllegalStateException) {
                                logger.error("Failed to create capture request", e)
                                updateStatus(DISCONNECTED)
                            } catch (e: CameraAccessException) {
                                logger.error("Failed to access camera for requesting preview", e)
                                updateStatus(DISCONNECTED)
                            }

                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                            logger.error("Create capture session failed")
                            updateStatus(DISCONNECTED)
                        }
                    }, mHandler)
        } catch (e: CameraAccessException) {
            logger.error("Failed to access camera to make a preview request", e)
            updateStatus(DISCONNECTED)
        }

    }

    // Poll whether the preview session should stop
    private fun pollDisconnect() {
        if (state.recordingTime > measurementTime || doStop) {
            updateStatus(DISCONNECTED)
        }
    }

    /**
     * Process an image and send it.
     * @param time time in milliseconds since the Unix Epoch that the preview image was captured.
     * @param rgba a linear array of RGBA values. Index 4*i + 0 = r, 4*i + 1 = g, 4*i + 2 = b,
     * 4*i + 3 = a.
     */
    private fun updatePreview(time: Long, rgba: ByteArray) {
        var totalR: Long = 0
        var totalG: Long = 0
        var totalB: Long = 0

        var i = 0
        while (i < rgba.size) {
            totalR += (rgba[i].toInt() and 0xFF).toLong()
            totalG += (rgba[i + 1].toInt() and 0xFF).toLong()
            totalB += (rgba[i + 2].toInt() and 0xFF).toLong()
            i += 4
        }

        val sampleSize = rgba.size / 4

        val range = 255.0 * sampleSize

        val r = (totalR / range).toFloat()
        val g = (totalG / range).toFloat()
        val b = (totalB / range).toFloat()

        val timeReceived = System.currentTimeMillis() / 1000.0

        logger.debug("Got RGB {} {} {}", r, g, b)

        send(ppgTopic, PhoneCameraPpg(time / 1000.0, timeReceived, sampleSize, r, g, b))
    }

    /** Get the first back-facing camera in the list of cameras returned by the camera manager.  */
    @Throws(CameraAccessException::class)
    private fun backFacingCamera(manager: CameraManager): String? {
        for (cameraId in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            if (characteristics.get(LENS_FACING) == LENS_FACING_BACK) {
                return cameraId
            }
        }
        return null
    }

    /**
     * Get the optimal supported image size for preferred statistics. This minimises
     * the Euclidian distance metric between possible sizes and the preferred size.
     */
    @Throws(CameraAccessException::class)
    private fun getImageSize(cameraId: String): Size? {
        val characteristics = cameraManager!!.getCameraCharacteristics(cameraId)

        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        if (map == null) {
            updateStatus(DISCONNECTED)
            return null
        }

        val sizes = map.getOutputSizes(Allocation::class.java)
        if (sizes.size == 0) {
            updateStatus(DISCONNECTED)
            return null
        }

        return chooseOptimalSize(sizes)
    }

    /**
     * Get the optimal image size out of the given sizes. This minimises
     * the Euclidian distance metric between possible sizes and the preferred size.
     */
    private fun chooseOptimalSize(sizes: Array<Size>): Size {
        var minDiff = java.lang.Double.MAX_VALUE
        var minSize: Size? = null
        val prefSize = preferredDimensions
        for (size in sizes) {
            val wDiff = (size.width - prefSize!!.width).toLong()
            val hDiff = (size.height - prefSize.height).toLong()

            val curDiff = wDiff * wDiff + hDiff * hDiff
            if (curDiff < minDiff) {
                minDiff = curDiff.toDouble()
                minSize = size
            }
            logger.debug("Available preview size {}x{}", size.width, size.height)
        }

        if (minSize == null) {
            throw IllegalStateException("Optimal image size cannot be determined.")
        }
        logger.debug("Chosen preview size {}x{}", minSize.width, minSize.height)
        return minSize
    }

    @Throws(IOException::class)
    override fun close() {
        super.close()

        state.actionListener = null

        mHandler!!.post {
            doStop = true
            if (mPreviewSession != null) {
                mPreviewSession = null
            }
            mCameraDevice?.close()

            mHandler!!.post {
                mProcessor!!.post {
                    if (mRenderContext != null) {
                        mRenderContext!!.close()
                        mRenderContext = null
                    }
                }
                mProcessorThread.quitSafely()

                mHandlerThread.quitSafely()
            }
        }

    }

    override fun startCamera() {
        mHandler!!.post {
            doStop = false
            if (!openCamera()) {
                updateStatus(DISCONNECTED)
            }
        }
    }

    override fun stopCamera() {
        mHandler!!.post { doStop = true }
    }

    @Synchronized
    internal fun configure(measurementTime: Int, measurementDimensions: Size) {
        this.measurementTime = TimeUnit.SECONDS.toMillis(measurementTime.toLong())
        this.preferredDimensions = measurementDimensions
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PhonePpgManager::class.java)
    }
}
