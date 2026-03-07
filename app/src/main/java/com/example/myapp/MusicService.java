package com.example.myapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

public class MusicService extends Service {
    private MediaPlayer mediaPlayer;
    private static final String CHANNEL_ID = "MusicChannel";
    private static final String TAG = "MusicService";
    private MediaSessionCompat mediaSession;

    private String currentSongName = "Unknown";

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        MusicService getService() { return MusicService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        mediaSession = new MediaSessionCompat(this, "MusicService");

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onSeekTo(long pos) {
                // This syncs the NOTIFICATION slider to the PLAYER
                seekTo((int) pos);
            }

            @Override
            public void onPause() {
                pauseResume();
            }

            @Override
            public void onPlay() {
                pauseResume();
            }
        });

        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            // Handle the button click from the notification
            if ("ACTION_PAUSE_RESUME".equals(action)) {
                pauseResume();
                return START_NOT_STICKY;
            }

            String name = intent.getStringExtra("NAME");
            if (name != null) {
                this.currentSongName = name;
            }

            String path = intent.getStringExtra("PATH");
            if (path != null) {

                playMusic(path);
            }
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String name) {
        Intent intent = new Intent(this, PlayerActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent pauseIntent = new Intent(this, MusicService.class);
        pauseIntent.setAction("ACTION_PAUSE_RESUME");
        pauseIntent.putExtra("NAME", name);
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        boolean isPlaying = (mediaPlayer != null && mediaPlayer.isPlaying());
        int playPauseIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        String statusText = isPlaying ? "Now Playing" : "Paused";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(playPauseIcon)
                .setContentTitle(name)
                .setContentText(statusText)
                .setContentIntent(contentIntent)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(playPauseIcon, isPlaying ? "Pause" : "Play", pausePendingIntent)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0))
                .build();
    }

    public void pauseResume() {
        if (mediaPlayer == null) return;

        if (mediaPlayer.isPlaying()) mediaPlayer.pause();
        else mediaPlayer.start();

        if (callback != null) {
            callback.onPlayerStatusChanged(mediaPlayer.isPlaying());
        }
        updatePlaybackState();
        updateNotification(currentSongName);
    }

    private void updateNotification(String name) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1, buildNotification(name));
        }
    }

    private void playMusic(String path) {
        if (mediaPlayer != null) mediaPlayer.release();
        mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();

                MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSongName)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mp.getDuration())
                        .build();
                mediaSession.setMetadata(metadata);
                updatePlaybackState();

                Notification notification = buildNotification(currentSongName);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(1, notification);
                }

                if (callback != null) callback.onPlayerStatusChanged(true);
            });

            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error playing file", e);
        }
    }

    public int getCurrentPosition() { return (mediaPlayer != null) ? mediaPlayer.getCurrentPosition() : 0; }
    public int getDuration() { return (mediaPlayer != null) ? mediaPlayer.getDuration() : 0; }
    public void seekTo(int pos) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(pos);

            updatePlaybackState();
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Music", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void updatePlaybackState() {
        if (mediaSession == null || mediaPlayer == null) return;

        long position = mediaPlayer.getCurrentPosition();
        float speed = mediaPlayer.isPlaying() ? 1.0f : 0.0f;
        int state = mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;

        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SEEK_TO |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(state, position, speed) // 'position' tells the notification where to move
                .build();

        mediaSession.setPlaybackState(playbackState);
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) mediaPlayer.release();
        if (mediaSession != null) mediaSession.release();
        super.onDestroy();
    }

    public interface MusicCallback { void onPlayerStatusChanged(boolean isPlaying); }
    private MusicCallback callback;
    public void setCallback(MusicCallback callback) { this.callback = callback; }
}