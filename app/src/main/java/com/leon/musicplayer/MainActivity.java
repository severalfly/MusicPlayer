package com.leon.musicplayer;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 主界面：选文件夹 / 选文件 → 歌曲列表 + 底部播放控制条。
 * 支持顺序循环、随机播放、单曲循环，点列表任意一首即播。
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_FOLDER = 1;
    private static final int REQ_FILE = 2;

    private static final String[] AUDIO_EXT = {
            ".mp3", ".wav", ".flac", ".ogg", ".oga", ".m4a", ".aac",
            ".opus", ".mid", ".midi", ".amr", ".3gp"
    };

    private final List<Song> songs = new ArrayList<>();
    private ArrayAdapter<Song> adapter;
    private TextView nowTitle;
    private ImageButton playButton;
    private TextView modeButton;
    private SeekBar seekBar;
    private boolean userSeeking;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshControls();
            handler.postDelayed(this, 400);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView listView = findViewById(android.R.id.list);
        TextView empty = findViewById(R.id.empty);
        listView.setEmptyView(empty);
        adapter = new ArrayAdapter<>(this, R.layout.item_song, R.id.text, songs);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, pos, id) -> playAt(pos));

        nowTitle = findViewById(R.id.now_title);
        playButton = findViewById(R.id.btn_play);
        modeButton = findViewById(R.id.btn_mode);
        seekBar = findViewById(R.id.seek);

        findViewById(R.id.btn_folder).setOnClickListener(v -> pickFolder());
        findViewById(R.id.btn_file).setOnClickListener(v -> pickFile());
        findViewById(R.id.btn_prev).setOnClickListener(v -> {
            if (MusicService.instance != null) {
                MusicService.instance.prev();
            }
        });
        findViewById(R.id.btn_next).setOnClickListener(v -> {
            if (MusicService.instance != null) {
                MusicService.instance.next();
            }
        });
        playButton.setOnClickListener(v -> {
            if (MusicService.instance != null) {
                MusicService.instance.togglePlay();
            }
        });
        modeButton.setOnClickListener(v -> {
            if (MusicService.instance != null) {
                MusicService.instance.cycleMode();
            } else {
                MusicService.mode = (MusicService.mode + 1) % 3;
                modeButton.setText(MusicService.modeLabel());
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                if (MusicService.instance != null) {
                    MusicService.instance.seekTo(sb.getProgress());
                }
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }

        // 预热服务，保证控制按钮可用
        startService(new Intent(this, MusicService.class));

        // 服务已在播放（比如从后台回到应用）：恢复列表显示
        MusicService running = MusicService.instance;
        if (running != null && running.getCount() > 0) {
            songs.addAll(running.getPlaylist());
            adapter.notifyDataSetChanged();
        }

        Uri data = getIntent().getData();
        if (data != null) {
            directPlay(data);
        }
        handler.postDelayed(tick, 300);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null) {
            directPlay(data);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    // ------------------------------------------------------------ 选择入口

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_FOLDER);
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, REQ_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQ_FOLDER) {
            Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            Toast.makeText(this, "正在扫描歌曲…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                List<Song> found = new ArrayList<>();
                try {
                    collect(tree, DocumentsContract.getDocumentId(tree), 0, found);
                } catch (Exception e) {
                    android.util.Log.e("MusicPlayer", "scan failed", e);
                }
                Collections.sort(found, (a, b) -> a.title.compareToIgnoreCase(b.title));
                runOnUiThread(() -> {
                    songs.clear();
                    songs.addAll(found);
                    adapter.notifyDataSetChanged();
                    if (found.isEmpty()) {
                        Toast.makeText(this, "这个文件夹里没有找到音频文件", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "找到 " + found.size() + " 首，点一首开始播放", 
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        } else if (requestCode == REQ_FILE) {
            directPlay(data.getData());
        }
    }

    /** 递归扫描 SAF 目录树里的音频文件（最多 5 层、500 首） */
    private void collect(Uri treeUri, String parentDocId, int depth, List<Song> out) {
        if (depth > 5 || out.size() >= 500) {
            return;
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
        Cursor c = getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        if (c == null) {
            return;
        }
        while (c.moveToNext() && out.size() < 500) {
            String docId = c.getString(0);
            String name = c.getString(1);
            String mime = c.getString(2);
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                collect(treeUri, docId, depth + 1, out);
            } else if (isAudioName(name)) {
                out.add(new Song(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                        stripExt(name)));
            }
        }
        c.close();
    }

    /** 外部直接带 URI 启动：播放该文件，并把同目录音频加入列表 */
    private void directPlay(Uri data) {
        List<Song> list = new ArrayList<>();
        int start = 0;
        if ("file".equals(data.getScheme()) && data.getPath() != null) {
            File f = new File(data.getPath());
            File parent = f.getParentFile();
            File[] siblings = parent == null ? null : parent.listFiles();
            if (siblings != null) {
                List<File> auds = new ArrayList<>();
                Collections.addAll(auds, siblings);
                Collections.sort(auds);
                for (File x : auds) {
                    if (x.isFile() && isAudioName(x.getName())) {
                        if (x.getName().equals(f.getName())) {
                            start = list.size();
                        }
                        list.add(new Song(Uri.fromFile(x), stripExt(x.getName())));
                    }
                }
            }
        }
        if (list.isEmpty()) {
            list.add(new Song(data, guessName(data)));
        }
        songs.clear();
        songs.addAll(list);
        adapter.notifyDataSetChanged();
        MusicService.pending = list;
        MusicService.pendingIndex = start;
        startService(new Intent(this, MusicService.class)
                .setAction(MusicService.ACTION_PLAY_PENDING));
    }

    private void playAt(int pos) {
        if (pos < 0 || pos >= songs.size()) {
            return;
        }
        MusicService.pending = new ArrayList<>(songs);
        MusicService.pendingIndex = pos;
        startService(new Intent(this, MusicService.class)
                .setAction(MusicService.ACTION_PLAY_PENDING));
    }

    // ------------------------------------------------------------ 工具

    private static boolean isAudioName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        for (String ext : AUDIO_EXT) {
            if (n.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private static String guessName(Uri uri) {
        String p = uri.getLastPathSegment();
        if (p == null) {
            return "未知曲目";
        }
        int i = p.lastIndexOf('/');
        return stripExt(i >= 0 ? p.substring(i + 1) : p);
    }

    private void refreshControls() {
        MusicService s = MusicService.instance;
        modeButton.setText(MusicService.modeLabel());
        if (s != null && s.hasCurrent()) {
            nowTitle.setText(s.getTitle() + " · " + MusicService.modeLabel());
            playButton.setImageResource(s.isPlaying()
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_media_play);
            if (!userSeeking) {
                seekBar.setMax(Math.max(1, s.getDuration()));
                seekBar.setProgress(s.getPosition());
            }
        } else {
            playButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }
}
