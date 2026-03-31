package com.example.mp3player;

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
    private boolean isRepeatEnabled = false;
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        MusicService getService() { return MusicService.this; }
    }

    public void toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled;
        if (mediaPlayer != null) {
            // This tells the hardware/engine to actually loop the file!
            mediaPlayer.setLooping(isRepeatEnabled);
        }
    }
    public boolean isRepeatEnabled() { return isRepeatEnabled; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        mediaSession = new MediaSessionCompat(this, "MusicService");

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onSeekTo(long pos) {
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
            if ("ACTION_PAUSE_RESUME".equals(action)) {
                pauseResume();
                return START_NOT_STICKY;
            }

            this.currentSongName = intent.getStringExtra("NAME") != null ? intent.getStringExtra("NAME") : "Unknown";
            String path = intent.getStringExtra("PATH");

            Notification notification = buildNotification(this.currentSongName);

            // Required for Android 14 (API 34) and 15+ (API 35/36)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(1, notification);
            }

            if (path != null) {
                playMusic(path);
            }
        }
        return START_STICKY; // Use START_STICKY to keep service alive
    }

    private Notification buildNotification(String name) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("NAME", name);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent pauseIntent = new Intent(this, MusicService.class);
        pauseIntent.setAction("ACTION_PAUSE_RESUME");
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        boolean isPlaying = (mediaPlayer != null && mediaPlayer.isPlaying());
        int playPauseIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(playPauseIcon)
                .setContentTitle(name)
                .setContentText(isPlaying ? "Now Playing" : "Paused")
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

        if (callback != null) callback.onPlayerStatusChanged(mediaPlayer.isPlaying());
        updatePlaybackState();
        updateNotification(currentSongName);
    }

    private void updateNotification(String name) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(1, buildNotification(name));
    }

    private void playMusic(String path) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);

            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSongName)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "My App")
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mp.getDuration())
                        .build());

                updatePlaybackState();
                updateNotification(currentSongName);
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
        int state = mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(state, mediaPlayer.getCurrentPosition(), 1.0f)
                .build());
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