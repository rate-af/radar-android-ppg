package com.vl.recordaf;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.CountDownTimer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.hardware.Camera;
import android.hardware.Camera.*;
import android.renderscript.*;
import android.graphics.ImageFormat;
import android.view.SurfaceView;
import android.widget.TextView;

import java.io.IOException;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button startButton;
    private Camera camera;
    private Parameters Para;
    private RenderScript rs;
    private SurfaceView sv;
    private Thread closeThread;
    private Allocation in;
    private Allocation out;
    private boolean started = false;
    private TextView mTextField;

    private List<Double>[] finalData;

    @Override
    protected void onResume()
    {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        12346);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.startButton);

        startButton.setOnClickListener(this);

        rs = RenderScript.create(getApplicationContext());

        sv = findViewById(R.id.sv);

        View ml = findViewById(R.id.mainLayout);
        ml.bringToFront();

        mTextField = findViewById(R.id.tv);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //sv.setVisibility(View.INVISIBLE);

    }

    @Override
    public void onClick(View v) {
        if (!started) {
            started = true;
            camera = Camera.open();
            Para = camera.getParameters();
            Para.setPreviewFormat(17);
            Log.e("checkF", "it worked! " + Para.getSupportedPictureFormats().size());
            camera.setDisplayOrientation(90);
            camera.setParameters(Para);
            Para = camera.getParameters();

            //camera.setPreviewDisplay(surfaceHolder);
            try {
                camera.setPreviewDisplay(sv.getHolder());
            } catch (IOException e) {
                Log.e("MainActivity", "settingPreviewFailed", e);
                camera.release();
                started = false;
                return;
            }

            Para.setFlashMode(Parameters.FLASH_MODE_TORCH);
            camera.setParameters(Para);

            finalData = new List[4];
            finalData[0] = new ArrayList<Double>();
            finalData[1] = new ArrayList<Double>();
            finalData[2] = new ArrayList<Double>();
            finalData[3] = new ArrayList<Double>();

            final int frameHeight = camera.getParameters().getPreviewSize().height;
            final int frameWidth = camera.getParameters().getPreviewSize().width;

            final int w = Para.getPreviewSize().width / 16;
            final int h = Para.getPreviewSize().height / 16;
            final int un = w * 16 * h;
            final double totalP = (16 * 4 * (h * w) * 255);

            final AtomicLong numFrames = new AtomicLong(0L);

            camera.setPreviewCallback(new PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    // convertion
                    Allocation bmData = renderScriptNV21ToRGBA888(
                            frameWidth,
                            frameHeight,
                            data);

                    final int t = bmData.getBytesSize();
                    byte[] temp = new byte[t];
                    bmData.copyTo(temp);


                    double[][] values = new double[3][16 * 16];


                    for (int i = 0; i < t / 4; i += 4)
                    {
                        values[0][((i / w) % 16) + (i / un) * 16] += temp[i * 4] & 0xFF;
                        values[1][((i / w) % 16) + (i / un) * 16] += temp[i * 4 + 1] & 0xFF;
                        values[2][((i / w) % 16) + (i / un) * 16] += temp[i * 4 + 2] & 0xFF;
                    }


                    double totalR = 0, totalG = 0, totalB = 0;
                    for (int i = 0; i < 16 * 16; i += 1)
                    {
                        totalR += values[0][i];
                        totalG += values[1][i];
                        totalB += values[2][i];
                    }
                    totalR /= totalP;
                    totalG /= totalP;
                    totalB /= totalP;

                    finalData[0].add((double)numFrames.incrementAndGet());
                    finalData[1].add(totalR);
                    finalData[2].add(totalG);
                    finalData[3].add(totalB);

                }
            });
            camera.startPreview();

            closeThread = new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(60000L);
                    } catch (InterruptedException e) {
                        //do nothing
                    }
                    camera.stopPreview();
                    camera.setPreviewCallback(null);
                    camera.release();
                    started = false;

                    double fps = numFrames.get() / 60;

                    Log.i("MainActivity", "frames : " + numFrames.get() + " - " + fps + " fps");

                    for (int i = 0; i < finalData[0].size(); i++) {
                        finalData[0].set(i, finalData[0].get(i)/fps);
                        Log.i("ok", "hello2 " + finalData[0].get(i) + "  " + finalData[1].get(i) + "  " + finalData[2].get(i) + "  " + finalData[3].get(i));
                    }
                }
            };
            closeThread.start();

            new CountDownTimer(60000, 1000) {

                public void onTick(long millisUntilFinished) {
                    mTextField.setText("seconds remaining: " + millisUntilFinished / 1000);
                }

                public void onFinish() {
                    mTextField.setText("done!");
                }
            }.start();
        }

    }

    public Allocation renderScriptNV21ToRGBA888(int width, int height, byte[] nv21) {

        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

        if (in == null || in.getBytesSize() != nv21.length) {
            Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs)).setX(nv21.length);
            in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

            Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
            out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        }
        in.copyFrom(nv21);

        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);
        return out;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if (closeThread != null && closeThread.isAlive())
            closeThread.interrupt();
    }

}
