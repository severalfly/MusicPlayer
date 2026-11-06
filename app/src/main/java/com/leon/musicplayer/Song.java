package com.leon.musicplayer;

import android.net.Uri;

/** 一首歌：来源 URI + 显示标题 */
public class Song {
    public final Uri uri;
    public final String title;

    public Song(Uri uri, String title) {
        this.uri = uri;
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }
}
