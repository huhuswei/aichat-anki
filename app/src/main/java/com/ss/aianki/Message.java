package com.ss.aianki;

import android.os.SystemClock;

public class Message {
    private String role;
    private String content;
    private String id;
    private Long ankiNoteId;  // 添加 Anki 笔记 ID
    private static long counter = 0;
    private String prompt;
    public Message(String role, String content) {
        this.role = role;
        this.content = content;
        this.id = role + "_" + System.currentTimeMillis() + "_" + (++counter);
        this.prompt = "";
        System.out.println("创建新消息: ID=" + this.id + ", Role=" + role);  // 添加日志
    }

    public Message(String role, String content, String prompt) {
        this.role = role;
        this.content = content;
        this.id = role + "_" + System.currentTimeMillis() + "_" + (++counter);
        this.prompt = prompt;
        System.out.println("从数据库加载消息: ID=" + id + ", Role=" + role);  // 添加日志
    }

    public Message(String role, String content, String prompt, String id) {
        this.role = role;
        this.content = content;
        this.id = id;
        this.prompt = prompt;
        System.out.println("从数据库加载消息: ID=" + id + ", Role=" + role);  // 添加日志
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getAnkiNoteId() {
        return ankiNoteId;
    }

    public void setAnkiNoteId(Long ankiNoteId) {
        this.ankiNoteId = ankiNoteId;
    }

    public String getPrompt() { return  prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

}