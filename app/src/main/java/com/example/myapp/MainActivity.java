package com.example.myapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor; // Added this
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore; // Added this
import android.util.Log; // For robust logging
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MP3App";
    private ListView listView;
    private final ArrayList<String> mp3Names = new ArrayList<>(); // Marked as final
    private final ArrayList<String> mp3Paths = new ArrayList<>(); // Marked as final
    private MediaPlayer mediaPlayer;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.mp3List);
        Button btnLoad = findViewById(R.id.btnLoad);

        requestAudioPermission();

        btnLoad.setOnClickListener(v -> readMP3FilesFromMediaStore());

        // Note: This might return 0 results if permission isn't granted yet
        readMP3FilesFromMediaStore();

        listView.setOnItemClickListener((parent, view, position, id) -> playMP3(mp3Paths.get(position)));
    }

    private void requestAudioPermission() {
        ArrayList<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    private void readMP3FilesFromMediaStore() {
        mp3Names.clear();
        mp3Paths.clear();

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = new String[] {
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA
        };

        // Using a try-with-resources for the cursor
        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                null,
                null,
                null)) {

            if (cursor != null) {
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameColumn);
                    String path = cursor.getString(dataColumn);

                    // Filter for MP3 files specifically if needed
                    if (name.toLowerCase().endsWith(".mp3")) {
                        mp3Names.add(name);
                        mp3Paths.add(path);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading MP3s from MediaStore", e);
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mp3Names);

        listView.setAdapter(adapter);
    }

    private void playMP3(String path) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Error playing MP3", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }
}