package com.example.myapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
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
    private final ArrayList<String> mp3Names = new ArrayList<>();
    private final ArrayList<String> mp3Paths = new ArrayList<>();
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.mp3List);
        Button btnLoad = findViewById(R.id.btnLoad);

        // Request permissions (now includes Notifications for Android 13+)
        requestAudioPermission();

        btnLoad.setOnClickListener(v -> readMP3FilesFromMediaStore());

        // Initial attempt to load files
        readMP3FilesFromMediaStore();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
            intent.putExtra("PATH", mp3Paths.get(position));
            intent.putExtra("NAME", mp3Names.get(position));
            startActivity(intent);
        });
    }

    private void requestAudioPermission() {
        ArrayList<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ needs Audio + Notification permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // Android 12 and below just needs Storage
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

        Uri collection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;


        String[] projection = new String[] {
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED // Needed for sorting
        };

        String selection = MediaStore.Audio.Media.DATA + " LIKE ? AND " +
                MediaStore.Audio.Media.DURATION + " >= ?";

        String[] selectionArgs = new String[] {
                "%/Download/%",
                "50000"
        };


        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";


        try (Cursor cursor = getContentResolver().query(collection, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);

                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameColumn);
                    String path = cursor.getString(dataColumn);

                    if (name != null && name.toLowerCase().endsWith(".mp3")) {
                        mp3Names.add(name);
                        mp3Paths.add(path);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error filtering MP3s", e);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_mp3, R.id.text_mp3_name, mp3Names);
        listView.setAdapter(adapter);
    }
}