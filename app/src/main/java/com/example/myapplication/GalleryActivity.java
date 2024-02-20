package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

public class GalleryActivity extends AppCompatActivity {

    ViewPager viewPager;

    ArrayList<String> fileArray = new ArrayList<>();
    ViewPagerAdapter viewPagerAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        File folder = new File(Environment.DIRECTORY_PICTURES + File.separator + "myapplication");

        // Define which columns to return after the query
        String[] projection = new String[]{
                MediaStore.Images.Media._ID, // ID to construct the full URI
        };

        // Filter results to only show images stored in the app's specific directory
        String selection = MediaStore.Images.Media.RELATIVE_PATH + "=?";
        String[] selectionArgs = new String[]{Environment.DIRECTORY_PICTURES + File.separator + "myapplication"};

        // Query the ContentResolver to access the images
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, // URI indicating which dataset to query
                projection, // Projection indicating which columns to return
                selection, // Selection clause specifying which rows to return
                selectionArgs, // Selection arguments
                null)) { // Sort order (null for default)

            // Check if the cursor is not null and move to the first row
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                do {
                    // Retrieve the ID from the cursor
                    long id = cursor.getLong(idColumn);

                    // Construct the URI for the current item
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    Log.d("TAG", "URI: " + contentUri.toString());
                    // Add the URI string to the list
                    fileArray.add(contentUri.toString());

                } while (cursor.moveToNext()); // Move to the next row in the cursor
            }
            Log.d("TAG", "file array: " + fileArray.toString());

        } catch (Exception e) {
            Log.e("TAG", "Error accessing media store", e);
        }
        viewPager = findViewById(R.id.viewPager);
        viewPagerAdapter = new ViewPagerAdapter(this, fileArray);
        viewPager.setAdapter(viewPagerAdapter);
    }

    private void createFileArray(File folder) {
        File[] files = folder.listFiles();
        for (File file : files) {
            fileArray.add(file.getAbsolutePath());
            Log.d("file", file.getAbsolutePath());
        }
        Log.d("fileArray", "FILE ARRAY "+ fileArray.toString());
        viewPager = findViewById(R.id.viewPager);
        viewPagerAdapter = new ViewPagerAdapter(this, fileArray);
        viewPager.setAdapter(viewPagerAdapter);
    }

}