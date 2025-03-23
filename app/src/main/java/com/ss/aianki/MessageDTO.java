package com.ss.aianki;

public class MessageDTO {
    private String role;
    private String content;
    private String id;
    private Long ankiNoteId;
    
    public MessageDTO(String role, String content, String id, Long ankiNoteId) {
        this.role = role;
        this.content = content;
        this.id = id;
        this.ankiNoteId = ankiNoteId;
    }
    
    // Getters
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getId() { return id; }
    public Long getAnkiNoteId() { return ankiNoteId; }
} 