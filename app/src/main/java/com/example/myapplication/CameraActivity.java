package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import android.app.AlertDialog;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";
    private Mat mRgba;
    private Mat mGray;
    private boolean shouldTakePicture = false;
    private static final int MY_PERMISSIONS_REQUEST = 100;

    private CameraBridgeViewBase mOpenCvCameraView;

    private ImageView takePictureButton;

    private ImageView galleryImageButton;

    public CameraActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    private void verifyPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        // Since your target is Android 13 (API level 34), consider checking for specific permissions only if needed.
        // Note: For Android 10 (API level 29) and above, consider using scoped storage approach and MediaStore API for file access.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), MY_PERMISSIONS_REQUEST);
        } else {
            Log.d(TAG, "All permissions granted");
            mOpenCvCameraView.setCameraPermissionGranted();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.frame_surface);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        verifyPermissions();

        takePictureButton = findViewById(R.id.camera_icon);
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shouldTakePicture = true;
                Log.d(TAG, "Take picture button clicked");

                mOpenCvCameraView.animate().alpha(0f).setDuration(1000).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        // Optionally, reset the view's visibility or alpha after the animation
                        mOpenCvCameraView.setAlpha(1f);
                    }
                });
            }
            // Your code to take picture
        });

        galleryImageButton = findViewById(R.id.gallery_icon);
        galleryImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(CameraActivity.this, GalleryActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        });


    }
    @Override
    public void onResume() {
        super.onResume();
        if (OpenCVLoader.initLocal()) {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mOpenCvCameraView.enableView();
            // Your code to run after successful OpenCV initialization, e.g., enable camera view
        } else {
            Log.d(TAG, "Internal OpenCV library not found. Make sure to include it properly.");
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
    }
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        if (shouldTakePicture) {
            takePicture(mRgba);
            shouldTakePicture = false;
        }
        return mRgba;
    }

    private void takePicture(Mat mRgba) {
        Mat saveImage = mRgba.clone();
        // Assuming the intention is to flip the image as if rotating by 90 degrees clockwise
        // For an actual 90-degree rotation, you would use Core.rotate()
        Core.flip(saveImage.t(), saveImage, 1); // This performs a transpose followed by a flip, effectively rotating the image by 90 degrees
        Imgproc.cvtColor(saveImage, saveImage, Imgproc.COLOR_RGBA2BGRA);

        saveImageToGallery(saveImage);
    }

    private void saveImageToGallery(Mat mRgba) {
        // Convert Mat to Bitmap
        Bitmap bitmap = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mRgba, bitmap);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "Image_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "myapplication");

        Uri uri = null;
        ContentResolver resolver = getContentResolver();

        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Failed to create new MediaStore record.");
            Log.d(TAG,"Image saved to gallery: " + uri.toString());
            try (OutputStream stream = resolver.openOutputStream(uri)) {
                if (stream == null) throw new IOException("Failed to open output stream.");

                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    throw new IOException("Failed to save bitmap.");
            }
        } catch (IOException e) {
            if (uri != null) {
                // Clean up in case of failure
                resolver.delete(uri, null, null);
            }
            Log.e(TAG, "Failed to save image to gallery", e);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        verifyPermissions();
    }

    private void processCameraPermission(@NonNull int[] grantResults) {
        Log.d(TAG, "processCameraPermission: " + grantResults.length);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted");
            mOpenCvCameraView.setCameraPermissionGranted();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("Camera Permission Needed")
                        .setMessage("This app needs the camera permission to function. Please grant camera permission to use this feature.")
                        .setPositiveButton("OK", (dialogInterface, i) ->
                                // Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(CameraActivity.this,
                                        new String[]{Manifest.permission.CAMERA},
                                        MY_PERMISSIONS_REQUEST))
                        .create()
                        .show();
            } else {
                // Explain to the user that the feature is unavailable because
                // the features requires a permission that the user has denied.
                // At the same time, respect the user's decision.
                showFinalRationale();
            }
        }
    }

    private void showFinalRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Feature Unavailable")
                .setMessage("This feature cannot be used without camera permission. If you want to use this feature, please enable camera permission from the app settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    // Take the user to the app's settings page
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }
}