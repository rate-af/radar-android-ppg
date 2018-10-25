package com.vl.recordaf;

import android.content.Context;
import android.graphics.ImageFormat;
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
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.support.annotation.NonNull;
import android.util.Size;
import android.view.Surface;

import org.radarcns.android.device.AbstractDeviceManager;
import org.radarcns.android.device.DeviceStatusListener;
import org.radarcns.kafka.ObservationKey;
import org.radarcns.passive.phone.ppg.PhonePpg;
import org.radarcns.topic.AvroTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;
import static android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.renderscript.RenderScript.CREATE_FLAG_LOW_POWER;
import static android.renderscript.RenderScript.ContextType.DEBUG;
import static org.radarcns.android.device.DeviceStatusListener.Status.CONNECTED;
import static org.radarcns.android.device.DeviceStatusListener.Status.CONNECTING;
import static org.radarcns.android.device.DeviceStatusListener.Status.DISCONNECTED;

public class PhonePpgManager extends AbstractDeviceManager<PhonePpgService, PhonePpgState> implements PhonePpgState.OnActionListener {
    private static final Logger logger = LoggerFactory.getLogger(PhonePpgManager.class);

    private static final long TOTAL_TIME = 5_000L;
    private static final int WIDTH = 200;
    private static final int HEIGHT = 200;

    private final AvroTopic<ObservationKey, PhonePpg> ppgTopic;
    private final CameraManager cameraManager;
    private final HandlerThread mHandlerThread;
    private final HandlerThread mProcessorThread;
    private Handler mHandler;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    private boolean doStop;
    private Handler mProcessor;
    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private RenderContext mRenderContext;

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

        mHandlerThread = new HandlerThread("PPG");
        mProcessorThread = new HandlerThread("PPG processing");

        ppgTopic = createTopic("android_phone_ppg", PhonePpg.class);
    }

    @Override
    public void start(@NonNull Set<String> acceptableIds) {
        getState().setActionListener(this);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mProcessorThread.start();
        mProcessor = new Handler(mProcessorThread.getLooper());
    }


    private boolean openCamera(int width, int height) {
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                logger.error("Failed to acquire camera open lock");
                return false;
            }
            String cameraId = backFacingCamera(cameraManager);

            if (cameraId == null) {
                updateStatus(DISCONNECTED);
                return false;
            }

            CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(cameraId);

            StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                updateStatus(DISCONNECTED);
                return false;
            }

            Size[] sizes = map.getOutputSizes(Allocation.class);
            if (sizes.length == 0) {
                updateStatus(DISCONNECTED);
                return false;
            }

            Size videoSize = chooseOptimalSize(sizes, width, height);

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
                    cameraOpenCloseLock.release();
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

    private void startPreview() {
        if (mCameraDevice == null) {
            return;
        }
        try {
            List<Surface> surfaces = new ArrayList<>();
            Surface readerSurface = mRenderContext.getSurface();
            surfaces.add(readerSurface);

            CaptureRequest.Builder requestBuilder = mCameraDevice.createCaptureRequest(TEMPLATE_PREVIEW);
            requestBuilder.addTarget(readerSurface);
            requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            CaptureRequest captureRequest = requestBuilder.build();

            logger.debug("Starting capture session");
            mCameraDevice.createCaptureSession(surfaces,
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
                            } catch (CameraAccessException e) {
                                logger.error("Failed to access camera for requesting preview", e);
                                updateStatus(DISCONNECTED);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession){
                            logger.warn("Create capture session failed");
                        }
                    }, mHandler);
        } catch (CameraAccessException e) {
            logger.error("Failed to access camera to make a preview request", e);
            updateStatus(DISCONNECTED);
        }
    }

    private void pollDisconnect() {
        if (getState().getRecordingTime() > TOTAL_TIME || doStop) {
            updateStatus(DISCONNECTED);
        }
    }

    private void updatePreview(long time, byte[] outBytes) {
        long totalR = 0, totalG = 0, totalB = 0;

        for (int i = 0; i < outBytes.length; i += 4) {
            totalR += outBytes[i] & 0xFF;
            totalG += outBytes[i + 1] & 0xFF;
            totalB += outBytes[i + 2] & 0xFF;
        }

        int sampleSize = outBytes.length / 4;

        double range = 255 * sampleSize;

        float r = (float) (totalR / range);
        float g = (float) (totalG / range);
        float b = (float) (totalB / range);


        double timeReceived = System.currentTimeMillis() / 1000d;

        logger.debug("Got RGB {} {} {}", r, g, b);

        send(ppgTopic, new PhonePpg(time / 1000d, timeReceived, sampleSize, r, g, b));
    }

    private String backFacingCamera(@NonNull CameraManager manager) throws CameraAccessException {
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            if (Objects.equals(characteristics.get(LENS_FACING), LENS_FACING_BACK)) {
                return cameraId;
            }
        }
        return null;
    }

    private Size chooseOptimalSize(Size[] sizes, long width, long height) {
        double minDiff = Double.MAX_VALUE;
        Size minSize = null;
        for (Size size : sizes) {
            long wDiff = size.getWidth() - width;
            long hDiff = size.getHeight() - height;

            long curDiff = wDiff * wDiff + hDiff * hDiff;
            if (curDiff < minDiff) {
                minDiff = curDiff;
                minSize = size;
            }
            logger.debug("Available preview size {}x{}", size.getWidth(), size.getHeight());
        }

        if (minSize != null) {
            logger.debug("Chosen preview size {}x{}", minSize.getWidth(), minSize.getHeight());
        }
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
            if (!openCamera(WIDTH, HEIGHT)) {
                updateStatus(DISCONNECTED);
            }
        });
    }

    @Override
    public void stopCamera() {
        mHandler.post(() -> doStop = true);
    }

    private static class RenderContext {
        private final RenderScript rs;
        private final Allocation in;
        private final Allocation out;
        private final byte[] outBytes;
        private final ScriptC_yuv2rgb yuvToRgbIntrinsic;

        private RenderContext(Context context, Size dimensions) {
            this.rs = RenderScript.create(context, DEBUG, CREATE_FLAG_LOW_POWER);

            Type.Builder inType = new Type.Builder(rs, Element.U8(rs))
                    .setX(dimensions.getWidth())
                    .setY(dimensions.getHeight())
                    .setYuvFormat(ImageFormat.YUV_420_888);

            in = Allocation.createTyped(rs, inType.create(),
                    Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_INPUT);

            Type.Builder outType = new Type.Builder(rs, Element.RGBA_8888(rs))
                    .setX(dimensions.getWidth())
                    .setY(dimensions.getHeight());

            out = Allocation.createTyped(rs, outType.create(), Allocation.USAGE_SCRIPT);

            outBytes = new byte[dimensions.getWidth() * dimensions.getHeight() * 4];

            yuvToRgbIntrinsic = new ScriptC_yuv2rgb(rs);
        }

        public void setImageHandler(RgbReceiver listener, Handler handler) {
            in.setOnBufferAvailableListener(a -> {
                long time = System.currentTimeMillis();
                handler.post(() -> listener.apply(time, getRgb()));
            });
        }

        public Surface getSurface() {
            return in.getSurface();
        }

        byte[] getRgb() {
            in.ioReceive();
            yuvToRgbIntrinsic.set_gCurrentFrame(in);
            yuvToRgbIntrinsic.forEach_yuv2rgb(out);
            out.copyTo(outBytes);

            return outBytes;
        }

        public void close() {
            in.setOnBufferAvailableListener(null);
        }
    }

    public interface RgbReceiver {
        void apply(long time, byte[] rbga);
    }
}
