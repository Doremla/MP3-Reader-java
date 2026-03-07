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
                if (fromUser && isBound) musicService.seekTo(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Pause/Resume control
        btnPause.setOnClickListener(v -> {
            if (isBound) musicService.pauseResume();
        });

        Intent intent = new Intent(this, MusicService.class);
        intent.putExtra("PATH", getIntent().getStringExtra("PATH"));
        intent.putExtra("NAME", getIntent().getStringExtra("NAME"));

        startService(intent);
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
}