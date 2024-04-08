package com.example.myapplication;


import static org.opencv.calib3d.Calib3d.RANSAC;
import static org.opencv.calib3d.Calib3d.findHomography;
import static org.opencv.core.CvType.CV_8UC4;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.panoramagl.PLManager;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import timber.log.Timber;

public class PanoramaActivity extends AppCompatActivity {

    private static final String TAG = "PanoramaActivity";

    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;

    private PLManager plManager;

    private AlertDialog progressDialog;

    private ImageView stichedImage;

    // button back
    private Button back;

    public native int stitchImages(long[] images, long result);

    // load model from onnx file
    private ArrayList<Mat> selectedImages;
    private String method;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.loadLibrary("opencvStitching");
        System.loadLibrary("onnxruntime");
        method = getIntent().getStringExtra("Method");

       // plManager = new PLManager(this);
        setContentView(R.layout.activity_panorama);
        stichedImage = findViewById(R.id.stiched_img);
        back = findViewById(R.id.back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

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
                                } catch (IOException e) {
                                    runOnUiThread(() -> {
                                        // Use YourActivityName.this instead of this to refer to the Activity context
                                        Toast.makeText(this, "Error stitching images", Toast.LENGTH_SHORT).show();
                                        progressDialog.dismiss();
                                    });
                                    finish();
                                }
                            }
                            Mat stitchedImage = new Mat();
                            int ret = 0;
                            if (method.equals("OpenCV")) {
                                ret = stitchImages(images, stitchedImage.getNativeObjAddr());
                            } else {
                                Pair<Integer,Mat> resPair = stitchImagesUsingOnnx(imgs);
                                ret = resPair.first;
                                stitchedImage = resPair.second;
                            }

                            if (ret == 1) {
                                runOnUiThread(() -> {
                                    // Use YourActivityName.this instead of this to refer to the Activity context
                                    Toast.makeText(this, "Error stitching images", Toast.LENGTH_SHORT).show();
                                    progressDialog.dismiss();
                                    finish();
                                });
                            }

                            Bitmap stitchedImageBmp = Bitmap.createBitmap(stitchedImage.cols(), stitchedImage.rows(), Bitmap.Config.ARGB_8888);
                            Utils.matToBitmap(stitchedImage, stitchedImageBmp);

                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                stichedImage.setImageBitmap(stitchedImageBmp);
                            });
                        });
                    } else {
                        Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show();
                        finish();
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
                InputStream inputStream = context.getContentResolver().openInputStream(uri);
                bitmap = BitmapFactory.decodeStream(inputStream);
            }
        }
        return bitmap;
    }

    private Pair<Integer, Mat> stitchImagesUsingOnnx(ArrayList<Mat> images) {
        Mat result = new Mat();
        try {
            byte[] lightGlueModel = loadModel("lightglue.onnx", this);
            OrtSession lightGlueSession = createONNXSession(lightGlueModel, this);
            // measure the time
            long startTime = System.nanoTime(); // Capture start time
            Mat imLeft = images.get(0);
            Mat imRight = images.get(1);
            OrtSession.Result res = matchImages(lightGlueSession, imRight, imLeft, this);

            Optional<OnnxValue> matchesOptional = res.get("matches0");
            Optional<OnnxValue> keypoints0 = res.get("kpts0");
            Optional<OnnxValue> keypoints1 = res.get("kpts1");

            if (!matchesOptional.isPresent() || !keypoints0.isPresent() || !keypoints1.isPresent()) {
                return new Pair<>(1, null);
            }

            OnnxTensor matchesTensor = (OnnxTensor) matchesOptional.get();
            LongBuffer matchesBuffer = matchesTensor.getLongBuffer();
            OnnxTensor keypoints0Tensor = (OnnxTensor) keypoints0.get();
            LongBuffer keypoints0Buffer = keypoints0Tensor.getLongBuffer();
            OnnxTensor keypoints1Tensor = (OnnxTensor) keypoints1.get();
            LongBuffer keypoints1Buffer = keypoints1Tensor.getLongBuffer();

            // Extract the keypoints and matches
            long[][] kpts0 = ((long[][][]) res.get(0).getValue())[0];
            long[][] kpts1 = ((long[][][]) res.get(1).getValue())[0];
            long[][] matches0 = (long[][]) res.get(2).getValue();
            // Gather the matching keypoints
            long[][] matchedKpts0 = new long[matches0.length][2];
            long[][] matchedKpts1 = new long[matches0.length][2];

            for (int i = 0; i < matches0.length; i++) {
                int index0 = (int) matches0[i][0];
                int index1 = (int) matches0[i][1];

                matchedKpts0[i] = kpts0[index0];
                matchedKpts1[i] = kpts1[index1];
            }

            saveKptsOnSourceImages(imLeft, imRight, matchedKpts0, matchedKpts1);

            MatOfPoint2f matchedKpts0Mat = new MatOfPoint2f();
            MatOfPoint2f matchedKpts1Mat = new MatOfPoint2f();
            for (int i = 0; i < matchedKpts0.length; i++) {
                matchedKpts0Mat.push_back(new MatOfPoint2f(new Point(matchedKpts0[i][0], matchedKpts0[i][1])));
                matchedKpts1Mat.push_back(new MatOfPoint2f(new Point(matchedKpts1[i][0], matchedKpts1[i][1])));
            }

            Mat homography = findHomography(matchedKpts1Mat, matchedKpts0Mat, RANSAC, 5);

            imLeft.convertTo(imLeft, CvType.CV_32F);

            imRight.convertTo(imRight, CvType.CV_32F);
            Mat invHomography = new Mat();
            Core.invert(homography, invHomography);
            Imgproc.warpPerspective(imRight, result, invHomography, new Size(imLeft.cols() + imRight.cols(), imLeft.rows() + imRight.rows()));

            // Place the left image onto the panorama
            Mat sub = result.submat(0, imLeft.rows(), 0, imLeft.cols());
            imLeft.copyTo(sub);

            result.convertTo(result, CvType.CV_8UC4);
            result = cropPanorama(result);
            // Save the panorama in RGB
            saveImageToGallery(result);

            long endTime = System.nanoTime(); // Capture end time
            long duration = (endTime - startTime);  // Total execution time in nano seconds
            Log.d("Model", "Model execution time: " + duration / 1000000 + " ms");
        } catch (IOException e) {
            // Handle IOException
            Log.e("Model", "Model loading failed", e);
            return new Pair<>(1, null);
        } catch (OrtException e) {
            // Handle OrtException
            Log.e("Model", "Model calling failed", e);
            return new Pair<>(1, null);
        }
        return new Pair<>(0, result);
    }
    
    private byte[] loadModel(String filename, Context context) throws IOException {
        AssetManager assetManager = context.getAssets();
        InputStream is = assetManager.open(filename);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buff = new byte[1024];
        int len = 0;
        while ((len = is.read(buff)) != -1) {
            baos.write(buff, 0, len);
        }

        Log.d("Model", "Model "+filename+" loaded successfully");
        return baos.toByteArray();
    }

    public OrtSession createONNXSession(byte[] model, Context context) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession session = env.createSession(model,new OrtSession.SessionOptions());
        return session;
    }

    public void saveKptsOnSourceImages(final Mat imLeft, final Mat imRight, final long[][] pointSet1, final long[][] pointSet2) throws IOException {
        String imgName = "out"; // replace with your image name

        // copy the images
        Mat imRightLocal = imRight.clone();
        Mat imLeftLocal = imLeft.clone();

        // Marking the detected features on the two images.
        for (long[] point : pointSet1) {
            Imgproc.circle(imRightLocal, new Point(point[0], point[1]), 4, new Scalar(255, 255, 0), -1);
        }
        for (long[] point : pointSet2) {
            Imgproc.circle(imLeftLocal, new Point(point[0], point[1]), 4, new Scalar(255, 255, 0), -1);
        }

        // Save the images in RGB
        saveImageToGallery(imLeftLocal);
        saveImageToGallery(imRightLocal);
    }



    private Bitmap loadImageFromAssets(String filename, Context context) throws IOException {
        AssetManager assetManager = context.getAssets();
        InputStream is = assetManager.open(filename);
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        is.close();
        return bitmap;
    }

    private void saveImageToGallery(Mat mRgba) {
        // Convert Mat to Bitmap
        Bitmap bitmap = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mRgba, bitmap);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "Image_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "myapplication");

        Uri uri = null;
        ContentResolver resolver = getContentResolver();

        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Failed to create new MediaStore record.");
            try (OutputStream stream = resolver.openOutputStream(uri)) {
                if (stream == null) throw new IOException("Failed to open output stream.");

                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream))
                    throw new IOException("Failed to save bitmap.");
            }
        } catch (IOException e) {
            if (uri != null) {
                // Clean up in case of failure
                resolver.delete(uri, null, null);
            }
            Log.e("main", "Failed to save image to gallery", e);
        }
    }
    private OrtSession.Result matchImages(OrtSession lightGlueSession, final Mat im0, final Mat im1, PanoramaActivity mainActivity) throws IOException, OrtException{
        // create a onnx map with the two images
        float[][][][] img0 = convertToGrayscaleAndResize(im0);
        float[][][][] img1 = convertToGrayscaleAndResize(im1);
        OrtEnvironment env = OrtEnvironment.getEnvironment();

        OnnxTensor image0Tensor = OnnxTensor.createTensor(env, img0);
        OnnxTensor image1Tensor = OnnxTensor.createTensor(env, img1);

        HashMap<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("image0", image0Tensor);
        inputs.put("image1", image1Tensor);

        OrtSession.Result result = lightGlueSession.run(inputs);

        // Extract keypoints from the result
        Log.d("Keypoints", "Matches extracted successfully");
        return result;
    }
    private float[][][][] convertToGrayscaleAndResize(final Mat colorMat) {
        Mat resultMat = colorMat.clone();

        Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_RGB2GRAY);
        // Imgproc.resize(colorMat, resizedMat, new org.opencv.core.Size(640, 480));
        resultMat.convertTo(resultMat, CvType.CV_32F);
        // Normalize the image to be in the range [0, 1]
        Core.divide(resultMat, new Scalar(255.0), resultMat);

        // Extract the pixel data and store it in a 2D float array
        float[][][][] floatArray = new float[1][1][resultMat.rows()][resultMat.cols()];
        for (int i = 0; i < resultMat.rows(); i++) {
            for (int j = 0; j < resultMat.cols(); j++) {
                floatArray[0][0][i][j] = (float) resultMat.get(i, j)[0];
            }
        }

        // Release the OpenCV Mat objects to free memory
        resultMat.release();
        return floatArray;
    }

    private Mat cropPanorama(Mat result) {
        // Convert the result to grayscale
        Mat grayResult = new Mat();
        Imgproc.cvtColor(result, grayResult, Imgproc.COLOR_RGB2GRAY);

        int left = 0;
        int right = grayResult.cols();
        int top = 0;
        int bottom = grayResult.rows();

        // Find the bounds of non-zero pixels
        for (int i = 0; i < grayResult.cols(); i++) {
            if (Core.countNonZero(grayResult.col(i)) > 0) {
                left = i;
                break;
            }
        }
        for (int i = grayResult.cols() - 1; i >= 0; i--) {
            if (Core.countNonZero(grayResult.col(i)) > 0) {
                right = i;
                break;
            }
        }
        for (int i = 0; i < grayResult.rows(); i++) {
            if (Core.countNonZero(grayResult.row(i)) > 0) {
                top = i;
                break;
            }
        }
        for (int i = grayResult.rows() - 1; i >= 0; i--) {
            if (Core.countNonZero(grayResult.row(i)) > 0) {
                bottom = i;
                break;
            }
        }

        // Create a new submat (cropped image)
        Mat croppedResult = new Mat(result, new Rect(left, top, right - left, bottom - top));

        // Release the gray intermediate image to free memory
        grayResult.release();

        // Return the cropped image
        return croppedResult;
    }


}

