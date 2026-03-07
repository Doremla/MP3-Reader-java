package com.example.myapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class PlayerActivity extends AppCompatActivity {
    private MusicService musicService;
    private boolean isBound = false;
    private SeekBar seekBar;
    private final Handler handler = new Handler();

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;
            musicService.setCallback(isPlaying -> {
                // Update your Play/Pause button icon here
                Button btnPause = findViewById(R.id.btnPause);
                if (isPlaying) {
                    btnPause.setText("Pause"); // set icon for future updates
                } else {
                    btnPause.setText("Play");  // set icon for future updates
                }
            });
            setupSeekBar();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        seekBar = findViewById(R.id.seekBar);
        TextView title = findViewById(R.id.songTitle);
        Button btnPause = findViewById(R.id.btnPause);

        title.setText(getIntent().getStringExtra("NAME"));

        // Slider control
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacksAndMessages(null); // Pause app slider updates while dragging
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isBound) {
                    musicService.seekTo(seekBar.getProgress());
                }
                updateSeekBarTask(); // Resume app slider updates
            }
        });
        // Pause/Resume control
        btnPause.setOnClickListener(v -> {
            if (isBound) musicService.pauseResume();
        });

        Intent intent = new Intent(this, MusicService.class);
        String path = getIntent().getStringExtra("PATH");
        if (path != null) {
            intent.putExtra("PATH", path);
            intent.putExtra("NAME", getIntent().getStringExtra("NAME"));
            startService(intent); // This triggers playMusic in service
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void setupSeekBar() {
        if (isBound && musicService != null) {
            seekBar.setMax(musicService.getDuration());
            updateSeekBarTask();
        }
    }

    private void updateSeekBarTask() {
        if (isBound && musicService != null) {

            int duration = musicService.getDuration();
            if (seekBar.getMax() != duration && duration > 0) {
                seekBar.setMax(duration);
            }

            seekBar.setProgress(musicService.getCurrentPosition());
            handler.postDelayed(this::updateSeekBarTask, 1000);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        handler.removeCallbacksAndMessages(null);
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);


        String name = intent.getStringExtra("NAME");
        if (name != null) {
            TextView title = findViewById(R.id.songTitle);
            title.setText(name);
        }
    }
}