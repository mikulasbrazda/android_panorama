package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import android.content.ContentUris;
import android.content.Context;
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

    ArrayList<Uri> fileArray = new ArrayList<>();
    ViewPagerAdapter viewPagerAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        fileArray = getImagesFromMyApplicationDirectory(this);

        viewPager = findViewById(R.id.viewPager);
        viewPagerAdapter = new ViewPagerAdapter(this, fileArray);
        viewPager.setAdapter(viewPagerAdapter);
    }

    public ArrayList<Uri> getImagesFromMyApplicationDirectory(Context context) {
        ArrayList<Uri> imageUris = new ArrayList<>();

        // The columns to retrieve
        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH
        };

        // Only query images that are in the specific directory
        String selection = MediaStore.Images.Media.RELATIVE_PATH + "=?";
        String[] selectionArgs = new String[]{Environment.DIRECTORY_PICTURES + File.separator + "myapplication" + File.separator};

        // Query the content resolver
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null)) {

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String relativePath = cursor.getString(relativePathColumn);


                // Construct the URI for the specific image
                Uri contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Long.toString(id));
                imageUris.add(contentUri);
            }
        } catch (Exception e) {
            Log.e("MyApp", "Failed to query images", e);
        }

        return imageUris;
    }

}