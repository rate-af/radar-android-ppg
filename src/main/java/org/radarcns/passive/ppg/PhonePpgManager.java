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

package org.radarcns.passive.ppg;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.support.annotation.NonNull;
import android.util.Size;

import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.kafka.ObservationKey;
import org.radarcns.topic.AvroTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;
import static android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static org.radarcns.android.device.DeviceStatusListener.Status.CONNECTED;
import static org.radarcns.android.device.DeviceStatusListener.Status.CONNECTING;
import static org.radarcns.android.device.DeviceStatusListener.Status.DISCONNECTED;
import static org.radarcns.passive.ppg.RenderContext.RENDER_CONTEXT_RELEASER;

public class PhonePpgManager extends AbstractDeviceManager<PhonePpgService, PhonePpgState> implements PhonePpgState.OnActionListener {
    private static final Logger logger = LoggerFactory.getLogger(PhonePpgManager.class);

    private final AvroTopic<ObservationKey, PhoneCameraPpg> ppgTopic;
    private final CameraManager cameraManager;
    private final HandlerThread mHandlerThread;
    private final HandlerThread mProcessorThread;
    private Size preferredDimensions;
    private Handler mHandler;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    private boolean doStop;
    private Handler mProcessor;
    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private RenderContext mRenderContext;
    private long measurementTime;

    /**
     * AppSource manager initialization. After initialization, be sure to call
     * {@link #setName(String)}.
     *
     * @param service service that the manager is started by
     */
    public PhonePpgManager(PhonePpgService service) {
        super(service);

        setName(Build.MODEL + " PPG");

        cameraManager = (CameraManager) getService().getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            updateStatus(DISCONNECTED);
        }

        updateStatus(DeviceStatusListener.Status.READY);

        getState().setStateChangeListener(new PhonePpgState.OnStateChangeListener() {
            @Override
            public void release() {
                RENDER_CONTEXT_RELEASER.setShouldRelease(true);
            }

            @Override
            public void acquire() {
                RENDER_CONTEXT_RELEASER.setShouldRelease(false);
            }
        });

        mHandlerThread = new HandlerThread("PPG");
        mProcessorThread = new HandlerThread("PPG processing");

        ppgTopic = createTopic("android_phone_ppg", PhoneCameraPpg.class);

        measurementTime = TimeUnit.SECONDS.toMillis(service.getMeasurementTime());
        preferredDimensions = service.getMeasurementDimensions();
    }

    @Override
    public void start(@NonNull Set<String> acceptableIds) {
        getState().setActionListener(this);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mProcessorThread.start();
        mProcessor = new Handler(mProcessorThread.getLooper());
    }


    private boolean openCamera() {
        try {
            // Do not open the camera if the camera open operation is still in progress
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                logger.error("Failed to acquire camera open lock");
                return false;
            }
            String cameraId = backFacingCamera(cameraManager);

            if (cameraId == null) {
                logger.error("Cannot get back-facing camera.");
                updateStatus(DISCONNECTED);
                return false;
            }

            Size videoSize = getImageSize(cameraId);
            if (videoSize == null) {
                logger.error("Cannot determine PPG image size.");
                updateStatus(DISCONNECTED);
                return false;
            }

            mRenderContext = new RenderContext(getService(), videoSize);
            mRenderContext.setImageHandler(this::updatePreview, mProcessor);

            updateStatus(CONNECTING);

            logger.debug("Opening camera");
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraOpenCloseLock.release();
                    mCameraDevice = camera;
                    startPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    mCameraDevice = null;
                    updateStatus(DISCONNECTED);
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    cameraOpenCloseLock.release();
                    mCameraDevice = null;
                    updateStatus(DISCONNECTED);
                }
            }, mHandler);

        } catch (CameraAccessException e) {
            logger.error("Cannot access the camera.", e);
            cameraOpenCloseLock.release();
            return false;
        } catch (SecurityException e) {
            logger.error("No access to camera device", e);
            cameraOpenCloseLock.release();
            return false;
        } catch (InterruptedException e) {
            logger.error("Could not acquire lock to camera", e);
            return false;
        }
        return true;
    }

    /** Start the preview session. This should only be called once the camera is open. */
    private void startPreview() {
        if (mCameraDevice == null) {
            return;
        }
        try {
            logger.debug("Starting capture session");
            mCameraDevice.createCaptureSession(Collections.singletonList(mRenderContext.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (mCameraDevice == null) {
                                return;
                            }
                            mPreviewSession = cameraCaptureSession;
                            updateStatus(CONNECTED);
                            logger.info("Started PPG capture session");

                            try {
                                // Make a capture request, sending images to the RenderContext and enabling the torch
                                CaptureRequest.Builder requestBuilder = mCameraDevice.createCaptureRequest(TEMPLATE_PREVIEW);
                                requestBuilder.addTarget(mRenderContext.getSurface());
                                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                                CaptureRequest captureRequest = requestBuilder.build();

                                mPreviewSession.setRepeatingRequest(captureRequest, new CameraCaptureSession.CaptureCallback() {
                                    @Override
                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                        logger.debug("Completed a capture");
                                        pollDisconnect();
                                    }

                                    @Override
                                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                                        pollDisconnect();
                                    }
                                }, mHandler);
                            } catch (IllegalStateException e) {
                                logger.error("Failed to create capture request", e);
                                updateStatus(DISCONNECTED);
                            } catch (CameraAccessException e) {
                                logger.error("Failed to access camera for requesting preview", e);
                                updateStatus(DISCONNECTED);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession){
                            logger.error("Create capture session failed");
                            updateStatus(DISCONNECTED);
                        }
                    }, mHandler);
        } catch (CameraAccessException e) {
            logger.error("Failed to access camera to make a preview request", e);
            updateStatus(DISCONNECTED);
        }
    }

    // Poll whether the preview session should stop
    private void pollDisconnect() {
        if (getState().getRecordingTime() > getMeasurementTime() || doStop) {
            updateStatus(DISCONNECTED);
        }
    }

    /**
     * Process an image and send it.
     * @param time time in milliseconds since the Unix Epoch that the preview image was captured.
     * @param rgba a linear array of RGBA values. Index 4*i + 0 = r, 4*i + 1 = g, 4*i + 2 = b,
     *            4*i + 3 = a.
     */
    private void updatePreview(long time, byte[] rgba) {
        long totalR = 0, totalG = 0, totalB = 0;

        for (int i = 0; i < rgba.length; i += 4) {
            totalR += rgba[i] & 0xFF;
            totalG += rgba[i + 1] & 0xFF;
            totalB += rgba[i + 2] & 0xFF;
        }

        int sampleSize = rgba.length / 4;

        double range = 255.0 * sampleSize;

        float r = (float) (totalR / range);
        float g = (float) (totalG / range);
        float b = (float) (totalB / range);

        double timeReceived = System.currentTimeMillis() / 1000d;

        logger.debug("Got RGB {} {} {}", r, g, b);

        send(ppgTopic, new PhoneCameraPpg(time / 1000d, timeReceived, sampleSize, r, g, b));
    }

    /** Get the first back-facing camera in the list of cameras returned by the camera manager. */
    private String backFacingCamera(@NonNull CameraManager manager) throws CameraAccessException {
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            if (Objects.equals(characteristics.get(LENS_FACING), LENS_FACING_BACK)) {
                return cameraId;
            }
        }
        return null;
    }

    /**
     * Get the optimal supported image size for preferred statistics. This minimises
     * the Euclidian distance metric between possible sizes and the preferred size.
     */
    private Size getImageSize(String cameraId) throws CameraAccessException {
        CameraCharacteristics characteristics =
                cameraManager.getCameraCharacteristics(cameraId);

        StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
            updateStatus(DISCONNECTED);
            return null;
        }

        Size[] sizes = map.getOutputSizes(Allocation.class);
        if (sizes.length == 0) {
            updateStatus(DISCONNECTED);
            return null;
        }

        return chooseOptimalSize(sizes);
    }

    /**
     * Get the optimal image size out of the given sizes. This minimises
     * the Euclidian distance metric between possible sizes and the preferred size.
     */
    @NonNull
    private Size chooseOptimalSize(@NonNull Size[] sizes) {
        double minDiff = Double.MAX_VALUE;
        Size minSize = null;
        Size prefSize = getPreferredDimensions();
        for (Size size : sizes) {
            long wDiff = size.getWidth() - prefSize.getWidth();
            long hDiff = size.getHeight() - prefSize.getHeight();

            long curDiff = wDiff * wDiff + hDiff * hDiff;
            if (curDiff < minDiff) {
                minDiff = curDiff;
                minSize = size;
            }
            logger.debug("Available preview size {}x{}", size.getWidth(), size.getHeight());
        }

        if (minSize == null) {
            throw new IllegalStateException("Optimal image size cannot be determined.");
        }
        logger.debug("Chosen preview size {}x{}", minSize.getWidth(), minSize.getHeight());
        return minSize;
    }

    @Override
    public void close() throws IOException {
        super.close();

        getState().setActionListener(null);

        mHandler.post(() -> {
            doStop = true;
            if (mPreviewSession != null) {
                mPreviewSession = null;
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
            }

            mHandler.post(() -> {
                mProcessor.post(() -> {
                    if (mRenderContext != null) {
                        mRenderContext.close();
                        mRenderContext = null;
                    }
                });
                mProcessorThread.quitSafely();

                mHandlerThread.quitSafely();
            });
        });

    }

    @Override
    public void startCamera() {
        mHandler.post(() -> {
            doStop = false;
            if (!openCamera()) {
                updateStatus(DISCONNECTED);
            }
        });
    }

    @Override
    public void stopCamera() {
        mHandler.post(() -> doStop = true);
    }

    public synchronized long getMeasurementTime() {
        return measurementTime;
    }

    public synchronized void configure(int measurementTime, Size measurementDimensions) {
        this.measurementTime = TimeUnit.SECONDS.toMillis(measurementTime);
        this.preferredDimensions = measurementDimensions;
    }

    public synchronized Size getPreferredDimensions() {
        return preferredDimensions;
    }

}
