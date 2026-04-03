    package com.example.mp3player;

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

    import androidx.annotation.NonNull;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.core.app.ActivityCompat;
    import androidx.core.content.ContextCompat;

    import java.util.ArrayList;

    //main page of the app, if i open the app the screen that pops up is this,
    //it loads the files and all the GUI at the beginning
    //NOTE: every override call is from a android function, so no rename of those methods or the code just breaks

    public class MainActivity extends AppCompatActivity {

        private ListView list;
        private ArrayAdapter<String> adapter;
        private final ArrayList<String> mp3Names = new ArrayList<>();
        private final ArrayList<String> mp3Paths = new ArrayList<>();
        private static final int permission_request_code = 100;

        @Override
        //start of the app, initialization of GUI and of the mp3 files, will not do if no permissions ar granted
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.my_toolbar);
            setSupportActionBar(toolbar);

            list = findViewById(R.id.mp3List);
            Button btnLoad = findViewById(R.id.btnLoad);
            if (checkPermission()) {
                readMP3s();
            } else {
                audioPermission();
            }

            btnLoad.setOnClickListener(v -> {
                readMP3s();
                if (adapter != null) {
                    adapter.getFilter().filter("");
                }
            });

            list.setOnItemClickListener((parent, view, position, id) -> {
                String selectedSongName = adapter.getItem(position);
                int originalIndex = mp3Names.indexOf(selectedSongName);

                if (originalIndex != -1) {
                    Intent serviceIntent = new Intent(MainActivity.this, MusicService.class);
                    serviceIntent.putStringArrayListExtra("Paths", mp3Paths);
                    serviceIntent.putStringArrayListExtra("Names", mp3Names);
                    serviceIntent.putExtra("INDEX", originalIndex);

                    startForegroundService(serviceIntent);

                    Intent activityIntent = new Intent(MainActivity.this, PlayerActivity.class);
                    activityIntent.putExtra("NAME", mp3Names.get(originalIndex)); // Changed to "NAME"
                    startActivity(activityIntent);
                }
            });
        }

        //do i have permissions? check if i have permissions
        private boolean checkPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
            } else {
                return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            }
        }
        //can you give me permissions? asks the user for permissions
        private void audioPermission() {
            ArrayList<String> permissionsToRequest = new ArrayList<>();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }

            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), permission_request_code);
            }
        }


        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] result) {
            super.onRequestPermissionsResult(requestCode, permissions, result);
            if (requestCode == permission_request_code) {
                if (result.length > 0 && result[0] == PackageManager.PERMISSION_GRANTED) {
                    readMP3s();
                }
            }
        }

        @Override
        public boolean onCreateOptionsMenu(android.view.Menu menu) {
            getMenuInflater().inflate(R.menu.main_menu, menu);
            android.view.MenuItem searchItem = menu.findItem(R.id.action_search);
            androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint("Search songs...");
                searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) { return false; }
                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (adapter != null) adapter.getFilter().filter(newText);
                        return true;
                    }
                });
            }
            return super.onCreateOptionsMenu(menu);
        }

        private void readMP3s() {
            mp3Names.clear();
            mp3Paths.clear();

            Uri collection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            String[] projection = new String[] {
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_ADDED
            };

            String selection = MediaStore.Audio.Media.DATA + " LIKE ? AND " +
                    MediaStore.Audio.Media.DURATION + " >= ?";

            String[] selectionArgs = new String[] { "%/Download/%", "50000" };
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
                Log.e("MP3 Reader", "Error filtering MP3s", e);
            }

            if (adapter == null) {
                adapter = new ArrayAdapter<>(this, R.layout.list_item_mp3, R.id.text_mp3_name, mp3Names);
                list.setAdapter(adapter);
            } else {
                adapter.notifyDataSetChanged();
            }
        }
    }