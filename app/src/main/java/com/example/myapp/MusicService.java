package com.example.myapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        mediaSession = new MediaSessionCompat(this, "MusicService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            // Handle the Pause/Play button from the notification.
            if ("ACTION_PAUSE_RESUME".equals(action)) {
                pauseResume();
                // Update the notification to show the correct play/pause icon
                updateNotification(intent.getStringExtra("NAME"));
                return START_NOT_STICKY;
            }

            String path = intent.getStringExtra("PATH");
            String name = intent.getStringExtra("NAME");

            if (path != null) {
                playMusic(path);
                Notification notification = buildNotification(name);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(1, notification);
                }
            }
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String name) {
        // Intent to open the app when clicking the notification
        Intent intent = new Intent(this, PlayerActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Intent to trigger Pause/Resume in this service
        Intent pauseIntent = new Intent(this, MusicService.class);
        pauseIntent.setAction("ACTION_PAUSE_RESUME");
        pauseIntent.putExtra("NAME", name);
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Determine which icon to show
        int playPauseIcon = (mediaPlayer != null && mediaPlayer.isPlaying())
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(name)
                .setContentText("Now Playing")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Add the Pause/Play button
                .addAction(playPauseIcon, "Pause/Resume", pausePendingIntent)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0))
                .build();
    }

    private void updateNotification(String name) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1, buildNotification(name));
        }
    }

    private void playMusic(String path) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Error playing file", e);
        }
    }

    public void pauseResume() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) mediaPlayer.pause();
        else mediaPlayer.start();
    }

    // Helper methods for Activity
    public int getCurrentPosition() { return (mediaPlayer != null) ? mediaPlayer.getCurrentPosition() : 0; }
    public int getDuration() { return (mediaPlayer != null) ? mediaPlayer.getDuration() : 0; }
    public boolean  isPlaying() { return mediaPlayer != null && mediaPlayer.isPlaying(); }
    public void seekTo(int pos) { if (mediaPlayer != null) mediaPlayer.seekTo(pos); }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Music", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) mediaPlayer.release();
        if (mediaSession != null) mediaSession.release();
        super.onDestroy();
    }
}