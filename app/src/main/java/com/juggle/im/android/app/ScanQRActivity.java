package com.juggle.im.android.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.juggle.im.android.R;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 扫一扫页面。
 * 使用 CameraX + ML Kit Barcode Scanning 实现二维码扫描。
 * 扫描结果通过 Intent extra "scan_result" 返回给调用方。
 */
public class ScanQRActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 1001;

    private PreviewView previewView;
    private ImageView scanLine;
    private ImageView btnFlash;

    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private boolean isFlashOn = false;
    private boolean isScanning = true;

    private ExecutorService analysisExecutor;
    private BarcodeScanner barcodeScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_qr);

        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.black));
        window.setNavigationBarColor(getColor(R.color.black));

        previewView = findViewById(R.id.previewView);
        scanLine = findViewById(R.id.scanLine);
        btnFlash = findViewById(R.id.btn_flash);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnFlash.setOnClickListener(v -> toggleFlash());

        // tips: 初始化 ML Kit 条码扫描器，仅识别 QR_CODE 类型以提升性能
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        analysisExecutor = Executors.newSingleThreadExecutor();

        if (checkCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }

        startScanAnimation();
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, R.string.scan_qr_permission_denied, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * 启动 CameraX 相机预览和图像分析。
     * 使用后置摄像头，绑定 Preview 和 ImageAnalysis 到生命周期。
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysis.setAnalyzer(analysisExecutor, imageProxy -> {
            if (!isScanning) {
                imageProxy.close();
                return;
            }
            @NonNull android.media.Image image = imageProxy.getImage();
            if (image == null) {
                imageProxy.close();
                return;
            }
            InputImage inputImage = InputImage.fromMediaImage(
                    image, imageProxy.getImageInfo().getRotationDegrees());

            barcodeScanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String raw = barcode.getRawValue();
                            if (raw != null && !raw.isEmpty()) {
                                onBarcodeDetected(raw);
                                break;
                            }
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        });

        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;

        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(this, selector, preview, analysis);

        // tips: 绑定相机后检查设备是否支持闪光灯，支持则显示闪光灯按钮
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            btnFlash.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 扫描线从扫描框顶部到底部循环移动的动画，周期 3 秒。
     */
    private void startScanAnimation() {
        View hole = findViewById(R.id.hole);
        hole.post(() -> {
            int[] loc = new int[2];
            hole.getLocationOnScreen(loc);
            int top = loc[1];
            int bottom = top + hole.getHeight();

            TranslateAnimation anim = new TranslateAnimation(
                    0, 0, 0, bottom - top - 4);
            anim.setDuration(3000);
            anim.setRepeatMode(Animation.RESTART);
            anim.setRepeatCount(Animation.INFINITE);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            scanLine.startAnimation(anim);
        });
    }

    /**
     * 二维码识别成功回调。
     * 震动反馈后将扫描结果返回给调用方。
     *
     * @param rawValue 二维码原始内容字符串
     */
    private void onBarcodeDetected(String rawValue) {
        if (!isScanning) return;
        isScanning = false;

        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib != null) {
            vib.vibrate(VibrationEffect.createOneShot(80,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        }

        Intent data = new Intent();
        data.putExtra("scan_result", rawValue);
        setResult(RESULT_OK, data);
        finish();
    }

    private void toggleFlash() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) return;
        isFlashOn = !isFlashOn;
        camera.getCameraControl().enableTorch(isFlashOn);
        btnFlash.setImageResource(isFlashOn
                ? R.drawable.ic_flash_on
                : R.drawable.ic_flash_off);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (analysisExecutor != null && !analysisExecutor.isShutdown()) {
            analysisExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}
