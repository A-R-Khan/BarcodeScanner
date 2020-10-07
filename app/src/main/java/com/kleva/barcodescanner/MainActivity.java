package com.kleva.barcodescanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.experimental.UseExperimental;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer {

    private static String [] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static int REQUEST_CODE_PERMISSIONS = 10;
    private static int REQUEST_CODE_SIGN_IN_GOOGLE = 9;
    private static  String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";

    private ImageCapture imageCapture;
    private File outputDirectory;
    private ExecutorService cameraExecutor;

    private MaterialButton actionButton;
    private PreviewView cameraPreviewView;

    private GoogleSignInClient mGoogleSignInClient;
    private String currentEmail = "";

    @NonNull private String previousBarcode = "";

    private BarcodeScannerOptions options =
            new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E)
                    .build();

    private BarcodeScanner scanner = BarcodeScanning.getClient(options);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        actionButton = findViewById(R.id.sign_in_button);
        cameraPreviewView = findViewById(R.id.camera_preview);

        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSignIn();
            }
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        outputDirectory = getOutputDirectory();

    }

    private void startSignIn() {

        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, REQUEST_CODE_SIGN_IN_GOOGLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SIGN_IN_GOOGLE) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                currentEmail = account.getEmail();

                // Signed in successfully, show authenticated UI.
            } catch (ApiException e) {
                // The ApiException status code indicates the detailed failure reason.
                // Please refer to the GoogleSignInStatusCodes class reference for more information.
                Log.e("GOOGLESIGNIN", "signInResult:failed code=" + e.getStatusCode());
            }
        }
    }

    private void startCamera() {

        ListenableFuture <ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {

                ProcessCameraProvider cameraProvider = null;
                try {
                    cameraProvider = cameraProviderFuture.get();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }

                Preview preview = new Preview.Builder()
                        .build();
                preview.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalyzer.setAnalyzer(cameraExecutor, MainActivity.this);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                try{
                    assert cameraProvider != null;
                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle(MainActivity.this, cameraSelector, preview, imageAnalyzer);
                }
                catch (Exception e) {
                    Log.e("BarCode", "Use case binding failed", e);
                }
            }

        }, ContextCompat.getMainExecutor(this));

    }

    private void takePhoto() {}

    private boolean allPermissionsGranted() {

        boolean res = true;
        for (String permission: REQUIRED_PERMISSIONS) {
            res = ContextCompat.checkSelfPermission(getBaseContext(), permission)
                    == PackageManager.PERMISSION_GRANTED;
            if (!res) {
                break;
            }
        }
        return res;
    }

    private File getOutputDirectory() {

        File [] externalMediaDirs = getExternalMediaDirs();
        File mediaDir = null;

        if (externalMediaDirs != null && externalMediaDirs.length > 0) {
            mediaDir = new File(externalMediaDirs[0], getResources().getString(R.string.app_name));
        }
        return (mediaDir != null && mediaDir.exists()) ? mediaDir : getFilesDir();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    @Override
    @UseExperimental(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    public void analyze(@NonNull ImageProxy imageProxy) {

        Image mediaImage = imageProxy.getImage();
        if(mediaImage != null) {
            InputImage image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            Task<List<Barcode>> result = scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {

                            if(barcode != null) {
                                if (! previousBarcode.equals(barcode.getDisplayValue())) {
                                    previousBarcode = barcode.getDisplayValue();

                                    // CAUTION HIGHLY UNSECURE NEVER USE THIS CLIENT SIDE UNLESS ITS FOR TESTING

                                    new SendMailTask().execute("myEmail@gmail.com",
                                            "myPassword",
                                            Collections.singletonList(currentEmail),
                                            "barcode result",
                                            barcode.getDisplayValue());
                                }
                            }
                        }
                        imageProxy.close();
                        mediaImage.close();
                    })
                    .addOnFailureListener(Throwable::printStackTrace);
        }

    }
}