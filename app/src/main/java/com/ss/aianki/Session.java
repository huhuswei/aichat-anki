package com.ss.aianki;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

public class Session {
    private String id;
    private String title;
    private long timestamp;
    private List<Message> messages;

    public Session(String firstQuestion) {
        this.id = String.valueOf(SystemClock.elapsedRealtime());
        this.timestamp = SystemClock.elapsedRealtime();
        this.messages = new ArrayList<>();
        // 取前10个字符作为标题，如果不足10个字符则全部使用
        String escapedTitle = firstQuestion.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        this.title = escapedTitle.length() > 200 ?
                escapedTitle.substring(0, 200) + "..." :
                escapedTitle;
    }

    public Session(String id, String title) {
        this.id = id;
        this.title = title.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        this.timestamp = SystemClock.elapsedRealtime();
        this.messages = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}