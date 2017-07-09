/**
 * Odoo, Open Source Management Solution
 * Copyright (C) 2012-today Odoo SA (<http:www.odoo.com>)
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 * <p/>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 * <p/>
 * Created on 9/7/17 23:22 PM
 */
package odoo.controls;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.odoo.R;

import java.io.IOException;

public class BarcodeScannerActivity extends AppCompatActivity
        implements SurfaceHolder.Callback, Detector.Processor<Barcode>, View.OnClickListener {

    public static final String TAG = BarcodeScannerActivity.class.getSimpleName();

    public static final int PERMISSIONS_REQUEST_CAMERA = 1;

    private int mBarcodeFormat = Barcode.ALL_FORMATS;

    private SurfaceView mCameraView;
    private SurfaceHolder mSurfaceHolder;

    private BarcodeDetector mBarcodeDetector;
    private CameraSource mCameraSource;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.barcode_scanner);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mBarcodeFormat = extras.getInt("barcode_format", Barcode.ALL_FORMATS);
        }

        mCameraView = (SurfaceView) findViewById(R.id.camera_view);

        mBarcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(mBarcodeFormat)
                .build();
        mBarcodeDetector.setProcessor(this);

        mCameraSource = new CameraSource.Builder(this, mBarcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setAutoFocusEnabled(true)
                .build();

        mCameraView.getHolder().addCallback(this);

        ImageView returnButton = (ImageView) findViewById(R.id.enter_button);
        returnButton.setOnClickListener(this);

        ImageView cancelButton = (ImageView) findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(this);
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void surfaceCreated(SurfaceHolder holder) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            mSurfaceHolder = holder;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
            return;
        }

        mCameraView.getHolder().addCallback(this);

        try {
            mCameraSource.start(mCameraView.getHolder());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    surfaceCreated(mSurfaceHolder);
                }
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mCameraSource.stop();
    }

    @Override
    public void release() {

    }

    @Override
    public void receiveDetections(Detector.Detections<Barcode> detections) {
        final SparseArray<Barcode> barcodes = detections.getDetectedItems();

        if (barcodes.size() == 1) {
            onBarcodeEnteredOrDetected(barcodes.valueAt(0));
        } else if (barcodes.size() > 1) {
            Toast.makeText(this, "More than one barcode detected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.enter_button:
                onEnterButtonClicked();
                break;
            case R.id.cancel_button:
                finish();
                break;
        }
    }

    private void onEnterButtonClicked() {
        EditText barcodeEditText = (EditText) findViewById(R.id.barcode_text);

        if (barcodeEditText != null) {
            String barcodeString = barcodeEditText.getText().toString();

            Barcode barcode = new Barcode();
            barcode.rawValue = barcodeString;
            barcode.displayValue = barcodeString;

            onBarcodeEnteredOrDetected(barcode);
        }
    }

    private void onBarcodeEnteredOrDetected(Barcode barcode) {
        Intent intent = new Intent("barcode_entered_detected");
        intent.putExtra("barcode", barcode);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        finish();
    }
}
