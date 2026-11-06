package com.leon.musicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 后台播放服务：持有 MediaPlayer 与播放列表，支持
 * 顺序循环 / 随机播放 / 单曲循环 三种模式，
 * 并注册 MediaSession 供通知栏、耳机按键等控制。
 */
public class MusicService extends Service {

    public static final String ACTION_PLAY_PENDING = "com.leon.musicplayer.PLAY_PENDING";
    public static final String ACTION_TOGGLE = "com.leon.musicplayer.TOGGLE";
    public static final String ACTION_NEXT = "com.leon.musicplayer.NEXT";
    public static final String ACTION_PREV = "com.leon.musicplayer.PREV";
    public static final String ACTION_STOP = "com.leon.musicplayer.STOP";

    public static final int MODE_ORDER = 0;
    public static final int MODE_SHUFFLE = 1;
    public static final int MODE_ONE = 2;

    /** 当前播放模式（进程内持久） */
    public static int mode = MODE_ORDER;

    /** 服务实例，Activity 直接读状态/调用控制 */
    public static volatile MusicService instance;
    /** Activity 传进来的待播放列表与起始下标 */
    public static List<Song> pending;
    public static int pendingIndex;

    private static final String TAG = "MusicPlayer";
    private static final String CHANNEL = "playback";
    private static final int NOTI_ID = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private MediaPlayer player;
    private MediaSession session;
    private NotificationManager nm;
    private List<Song> songs = Collections.emptyList();
    private int index;
    private String currentTitle;
    private int duration;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "service created");
        nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "播放控制",
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);

        session = new MediaSession(this, "MusicPlayer");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                resumePlay();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                prev();
            }

            @Override
            public void onFastForward() {
                seekBy(10000);
            }

            @Override
            public void onRewind() {
                seekBy(-10000);
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }

            @Override
            public void onStop() {
                stopPlayback();
            }
        });
        session.setActive(true);

        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        player.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnCompletionListener(mp -> handler.post(this::onSongEnd));
        player.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "player error what=" + what + " extra=" + extra);
            stopPlayback();
            return true;
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PLAY_PENDING.equals(action)) {
            // 必须先进入前台（Android 12 起强制）
            startForeground(NOTI_ID, buildNotification());
            if (pending != null && !pending.isEmpty()) {
                songs = pending;
                pending = null;
                play(pendingIndex);
            }
        } else if (ACTION_TOGGLE.equals(action)) {
            startForeground(NOTI_ID, buildNotification());
            togglePlay();
        } else if (ACTION_NEXT.equals(action)) {
            startForeground(NOTI_ID, buildNotification());
            next();
        } else if (ACTION_PREV.equals(action)) {
            startForeground(NOTI_ID, buildNotification());
            prev();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        }
        return START_STICKY;
    }

    // ------------------------------------------------------------ 对外控制

    public void setPlaylist(List<Song> list, int startIndex) {
        songs = list;
        play(startIndex);
    }

    public boolean hasCurrent() {
        return currentTitle != null;
    }

    public boolean isPlaying() {
        try {
            return currentTitle != null && player.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public String getTitle() {
        return currentTitle;
    }

    public int getPosition() {
        try {
            return currentTitle == null ? 0 : player.getCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
    }

    public int getDuration() {
        return duration;
    }

    public int getCount() {
        return songs.size();
    }

    public List<Song> getPlaylist() {
        return songs;
    }

    public int getIndex() {
        return index;
    }

    public void togglePlay() {
        if (isPlaying()) {
            pause();
        } else {
            resumePlay();
        }
    }

    public void pause() {
        if (isPlaying()) {
            player.pause();
        }
        updateAll();
    }

    public void resumePlay() {
        if (currentTitle == null) {
            if (!songs.isEmpty()) {
                play(index);
            }
        } else {
            player.start();
        }
        updateAll();
    }

    public void next() {
        if (songs.isEmpty()) {
            return;
        }
        if (songs.size() == 1) {
            play(0);
            return;
        }
        if (mode == MODE_SHUFFLE) {
            int n;
            do {
                n = random.nextInt(songs.size());
            } while (n == index);
            play(n);
        } else {
            play((index + 1) % songs.size());
        }
    }

    public void prev() {
        if (songs.isEmpty()) {
            return;
        }
        if (getPosition() > 3000) {
            seekTo(0);
            return;
        }
        play((index - 1 + songs.size()) % songs.size());
    }

    /** 切换播放模式，返回模式名 */
    public String cycleMode() {
        mode = (mode + 1) % 3;
        updateAll();
        return modeLabel();
    }

    public void seekTo(int ms) {
        try {
            player.seekTo(ms);
            updateSession();
        } catch (Exception ignored) {
        }
    }

    public void seekBy(int delta) {
        seekTo(Math.max(0, Math.min(getDuration(), getPosition() + delta)));
    }

    public static String modeLabel() {
        switch (mode) {
            case MODE_SHUFFLE:
                return "随机播放";
            case MODE_ONE:
                return "单曲循环";
            default:
                return "顺序循环";
        }
    }

    // ------------------------------------------------------------ 内部逻辑

    private void play(int i) {
        if (songs.isEmpty()) {
            return;
        }
        index = ((i % songs.size()) + songs.size()) % songs.size();
        Song s = songs.get(index);
        try {
            player.reset();
            player.setDataSource(this, s.uri);
            player.prepare();
            duration = player.getDuration();
            currentTitle = s.title;
            player.start();
            Log.i(TAG, "playing [" + (index + 1) + "/" + songs.size() + "] " + s.title
                    + " mode=" + modeLabel() + " dur=" + duration);
        } catch (Exception e) {
            Log.e(TAG, "cannot play " + s.uri, e);
            currentTitle = null;
        }
        updateAll();
    }

    private void onSongEnd() {
        if (currentTitle == null) {
            return;
        }
        if (mode == MODE_ONE) {
            seekTo(0);
            player.start();
        } else {
            next();
        }
    }

    private void stopPlayback() {
        try {
            player.stop();
        } catch (Exception ignored) {
        }
        currentTitle = null;
        duration = 0;
        updateSession();
        stopForeground(true);
        nm.cancel(NOTI_ID);
        stopSelf();
    }

    private void updateAll() {
        updateSession();
        nm.notify(NOTI_ID, buildNotification());
    }

    private void updateSession() {
        PlaybackState.Builder b = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_FAST_FORWARD
                        | PlaybackState.ACTION_REWIND
                        | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_STOP);
        boolean playing = isPlaying();
        int state = currentTitle == null ? PlaybackState.STATE_NONE
                : playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        b.setState(state, getPosition(), playing ? 1f : 0f);
        session.setPlaybackState(b.build());
    }

    private Notification buildNotification() {
        boolean playing = isPlaying();
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(playing ? android.R.drawable.ic_media_play
                        : android.R.drawable.ic_media_pause)
                .setContentTitle(currentTitle == null ? getString(R.string.app_name) : currentTitle)
                .setContentText(songs.isEmpty() ? ""
                        : (index + 1) + "/" + songs.size() + " · " + modeLabel())
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        addAction(b, "上一首", ACTION_PREV, android.R.drawable.ic_media_previous, 1);
        addAction(b, playing ? "暂停" : "播放", ACTION_TOGGLE,
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, 2);
        addAction(b, "下一首", ACTION_NEXT, android.R.drawable.ic_media_next, 3);
        b.setStyle(new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private void addAction(Notification.Builder b, String title, String action,
                           int icon, int requestCode) {
        Intent i = new Intent(this, MusicService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(this, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.addAction(new Notification.Action(icon, title, pi));
    }

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        try {
            session.release();
        } catch (Exception ignored) {
        }
        try {
            player.release();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
