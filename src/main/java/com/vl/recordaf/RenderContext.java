package com.vl.recordaf;

import android.content.Context;
import android.graphics.ImageFormat;
import android.os.Build;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Size;
import android.view.Surface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static android.renderscript.RenderScript.CREATE_FLAG_LOW_POWER;
import static android.renderscript.RenderScript.ContextType.DEBUG;

/**
 * Render context to accept images from a camera preview and convert them to RGB.
 * This context requires that the camera record image data in {@link ImageFormat#YUV_420_888}
 * format.
 */
class RenderContext {
    private static final Logger logger = LoggerFactory.getLogger(RenderContext.class);

    public static final CountedReleaser RENDER_CONTEXT_RELEASER = new CountedReleaser(() -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            logger.info("Releasing RenderScript context");
            RenderScript.releaseAllContexts();
        }
    });

    private final RenderScript rs;
    private final Allocation in;
    private final Allocation out;
    private byte[] outBytes;
    private final ScriptC_yuv2rgb yuvToRgbIntrinsic;

    /**
     * Constructor.
     * @param context rendering context.
     * @param dimensions preview image dimension size.
     */
    RenderContext(Context context, Size dimensions) {
        RENDER_CONTEXT_RELEASER.acquire();
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

    /**
     * Set callback to handle each RGB image.
     *
     * @param listener callback
     * @param handler thread to call the callback on.
     */
    public void setImageHandler(RgbReceiver listener, Handler handler) {
        in.setOnBufferAvailableListener(a -> {
            long time = System.currentTimeMillis();
            handler.post(() -> listener.apply(time, getRgb()));
        });
    }

    /**
     * Get surface to write YUV data to.
     */
    public Surface getSurface() {
        return in.getSurface();
    }

    /**
     * Converts the current image in the input buffer to an RGB byte array. The byte array
     * should not be stored, but immediately analyzed or copied.
     */
    private byte[] getRgb() {
        in.ioReceive();
        yuvToRgbIntrinsic.set_gCurrentFrame(in);
        yuvToRgbIntrinsic.forEach_yuv2rgb(out);
        out.copyTo(outBytes);

        return outBytes;
    }

    /**
     * Close the context and destroy any resources associated.
     */
    public void close() {
        in.setOnBufferAvailableListener(null);
        in.destroy();
        out.destroy();
        yuvToRgbIntrinsic.destroy();
        outBytes = null;
        rs.destroy();
        RENDER_CONTEXT_RELEASER.release();
    }

    public interface RgbReceiver {
        /**
         * Receive an RGB image.
         * @param time time in milliseconds since the Unix Epoch that the preview image was captured.
         * @param rgba a linear array of RGBA values. Index 4*i + 0 = r, 4*i + 1 = g, 4*i + 2 = b,
         *            4*i + 3 = a.
         */
        void apply(long time, byte[] rgba);
    }
}
