package com.example.mp3player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.graphics.Color;
import android.content.res.ColorStateList;
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
            updateRepeatButtonUI(findViewById(R.id.btnRepeat));
            musicService.setCallback(isPlaying -> {

                Button btnPause = findViewById(R.id.btnPause);
                if (isPlaying) {
                    btnPause.setText(getString(R.string.status_pause));
                } else {
                    btnPause.setText(getString(R.string.status_play));
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
        Button btnPause = findViewById(R.id.btnPause);
        Button btnRepeat = findViewById(R.id.btnRepeat);

        handleIntentData(getIntent());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacksAndMessages(null);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isBound) musicService.seekTo(seekBar.getProgress());
                updateSeekBarTask();
            }
        });

        btnPause.setOnClickListener(v -> {
            if (isBound) musicService.pauseResume();
        });

        btnRepeat.setOnClickListener(v -> {
            if (isBound) {
                musicService.toggleRepeat();
                updateRepeatButtonUI(btnRepeat);
            }
        });

        Intent intent = new Intent(this, MusicService.class);
        String path = getIntent().getStringExtra("PATH");
        if (path != null) {
            intent.putExtra("PATH", path);
            intent.putExtra("NAME", getIntent().getStringExtra("NAME"));
            startService(intent);
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }
    private void handleIntentData(Intent intent) {
        if (intent != null && intent.hasExtra("NAME")) {
            String name = intent.getStringExtra("NAME");
            TextView title = findViewById(R.id.songTitle);
            if (title != null && name != null) {
                title.setText(name);
            }
        }
    }



    private void updateRepeatButtonUI(Button btn) {
        if (musicService != null && musicService.isRepeatEnabled()) {
            btn.setText(getString(R.string.repeat_on));
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D0BCFF")));
            btn.setTextColor(Color.BLACK);
        } else {
            btn.setText(getString(R.string.repeat_off));
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A4458")));
            btn.setTextColor(Color.WHITE);
        }
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
            handler.postDelayed(this::updateSeekBarTask, 600);
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
        handleIntentData(intent);
    }
}