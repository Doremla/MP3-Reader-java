package com.example.mp3player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.support.v4.media.session.PlaybackStateCompat;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.util.ArrayList;

//this code is the prt that keeps the music player as a service, without this the music player
//will nor be able to replay music also as notification player but also just in background properly
public class MusicService extends Service {
    private MediaPlayer mediaPlayer;
    private boolean isChangingTrack = false;
    private static final String CHANNEL_ID = "MusicChannel";
    private static final String TAG = "MusicService";
    private MediaSessionCompat mediaSession;

    private String currentSongName = "Unknown";
    private boolean isRepeatEnabled = false;
    private final IBinder binder = new LocalBinder();
    private ArrayList<String> songPaths = new ArrayList<>();
    private ArrayList<String> songNames = new ArrayList<>();
    private int currentIndex = 0;

    public class LocalBinder extends Binder {
        MusicService getService() { return MusicService.this; }
    }

    public void toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled;
        if (mediaPlayer != null) {
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

            ArrayList<String> paths = intent.getStringArrayListExtra("Paths");
            ArrayList<String> names = intent.getStringArrayListExtra("Names");
            int index = intent.getIntExtra("INDEX", -1);
            if (paths != null && names != null && index != -1) {
                String requestedPath = paths.get(index);
                String currentPath = (songPaths.size() > currentIndex) ? songPaths.get(currentIndex) : "";

                if (!requestedPath.equals(currentPath) || mediaPlayer == null) {
                    this.songPaths = paths;
                    this.songNames = names;
                    this.currentIndex = index;
                    this.currentSongName = songNames.get(currentIndex);
                    playMusic(requestedPath);
                }
            }
        }
        return START_STICKY;
    }

    private void playMusic(String path) {
        isChangingTrack = true;

        if (mediaPlayer != null) {
            mediaPlayer.setOnCompletionListener(null);
            mediaPlayer.setOnPreparedListener(null);
            mediaPlayer.setOnErrorListener(null);
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!isRepeatEnabled && !isChangingTrack) {
                    playNext();
                } else if (isRepeatEnabled) {
                    mp.start();
                }
            });

            mediaPlayer.setOnPreparedListener(mp -> {
                isChangingTrack = false;
                mp.setLooping(isRepeatEnabled);
                mp.start();
                updatePlaybackState();
                updateNotification(currentSongName);
                if (callback != null) callback.onPlayerStatusChanged(true);
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                isChangingTrack = false;
                return false;
            });

            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            isChangingTrack = false;
            Log.e(TAG, "Source error", e);
        }
    }

    public void playNext() {
        if (songPaths == null || songPaths.isEmpty()) return;
        currentIndex = (currentIndex + 1) % songPaths.size();
        currentSongName = songNames.get(currentIndex);

        if (callback != null) callback.onTrackChanged(currentSongName);
        playMusic(songPaths.get(currentIndex));
    }

    public void pauseResume() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) mediaPlayer.pause();
        else mediaPlayer.start();

        if (callback != null) callback.onPlayerStatusChanged(mediaPlayer.isPlaying());
        updatePlaybackState();
        updateNotification(currentSongName);
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
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Music", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void updateNotification(String name) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(1, buildNotification(name));
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

    public interface MusicCallback {
        void onPlayerStatusChanged(boolean isPlaying);
        void onTrackChanged(String newName);
    }

    private MusicCallback callback;
    public void setCallback(MusicCallback callback) { this.callback = callback; }
}