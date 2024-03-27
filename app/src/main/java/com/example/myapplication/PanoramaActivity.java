package com.example.myapplication;


import static org.opencv.core.Core.hconcat;
import static org.opencv.core.CvType.CV_8UC4;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.panoramagl.PLManager;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class PanoramaActivity extends AppCompatActivity {

    private static final String TAG = "PanoramaActivity";

    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;

    private PLManager plManager;

    private AlertDialog progressDialog;

    private ImageView stichedImage;

    public native int stitchImages(long[] images, long result);

    private ArrayList<Mat> selectedImages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.loadLibrary("myapplication");
       // plManager = new PLManager(this);
        setContentView(R.layout.activity_panorama);
        stichedImage = findViewById(R.id.stiched_img);
                //plManager.onCreate();
        setupProgressDialog();

        registerResult();
        pickImages();

    }

    private void setupProgressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        RelativeLayout progressLayout = (RelativeLayout) inflater.inflate(R.layout.layout_progress_dialog, null);
        builder.setView(progressLayout);
        builder.setCancelable(false); // This makes the dialog non-cancelable

        progressDialog = builder.create();
    }
    @Override
    protected void onResume() {
        super.onResume();
      //  plManager.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        //plManager.onPause();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        //plManager.onDestroy();
    }
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        return plManager.onTouchEvent(event);
//    }
    void pickImages() {
        pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    void registerResult() {
        pickMedia =
                registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(), result -> {
                    // Callback is invoked after the user selects a media item or closes the
                    // photo picker.
                    if (result != null && !result.isEmpty()) {
                        progressDialog.show();


                        AsyncTask.execute(() -> {
                            long[] images = new long[result.size()];
                            ArrayList<Mat> imgs = new ArrayList<>(result.size());

                            for (int i = 0; i < result.size(); i++) {
                                try {
                                    Bitmap bitmap = getBitmapFromUri(result.get(i), this);
                                    Log.d(TAG, "pickImages: " + bitmap.getWidth() + " " + bitmap.getHeight());
                                    Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                                    Mat img = new Mat(bmp32.getHeight(), bmp32.getWidth(), CV_8UC4);
                                    Utils.bitmapToMat(bmp32, img);
                                    imgs.add(img);
                                    images[i] = imgs.get(i).getNativeObjAddr();
                                    Log.d(TAG, "pickImages: " + images[i] + " " + imgs.get(i).getNativeObjAddr() + " " + img.getNativeObjAddr());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            Mat stitchedImage = new Mat();

                            int ret = stitchImages(images, stitchedImage.getNativeObjAddr());

                            Log.d(TAG, "stitchImages: " + ret);
                            if (ret == 1) {
                                runOnUiThread(() -> {
                                    // Use YourActivityName.this instead of this to refer to the Activity context
                                    Toast.makeText(this, "Error stitching images", Toast.LENGTH_SHORT).show();
                                    progressDialog.dismiss();
                                });
                                return;
                            }
                            Bitmap stitchedImageBmp = Bitmap.createBitmap(stitchedImage.cols(), stitchedImage.rows(), Bitmap.Config.ARGB_8888);
                            Utils.matToBitmap(stitchedImage, stitchedImageBmp);

                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                stichedImage.setImageBitmap(stitchedImageBmp); // Ensure stichedImage (ImageView) is correctly initialized and accessible
                            });
                        });
//                        PLSphericalPanorama panorama = new PLSphericalPanorama();
//                        panorama.getCamera().lookAt(30.0f, 90.0f);
//
//                        panorama.setImage(new PLImage(stitchedImageBmp, false));

                        //plManager.setPanorama(panorama);
                    } else {
                        Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    public Bitmap getBitmapFromUri(Uri uri, Context context) throws IOException {
        Bitmap bitmap = null;
        if (uri != null) {
            // Use the MediaStore to retrieve the bitmap if it's a media Uri
            if (uri.toString().startsWith("content://media")) {
                bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
            } else {
                // For non-media Uris, use a FileInputStream
                try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(inputStream);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    // Handle the exception as needed
                }
            }
        }
        return bitmap;
    }
}

