package com.ss.aianki;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "chat.db";
    private static final int DATABASE_VERSION = 2;

    // 会话表
    private static final String TABLE_SESSIONS = "sessions";
    private static final String COLUMN_SESSION_ID = "id";
    private static final String COLUMN_SESSION_TITLE = "title";
    private static final String COLUMN_SESSION_TIMESTAMP = "timestamp";

    // 消息表
    private static final String TABLE_MESSAGES = "messages";
    private static final String COLUMN_MESSAGE_ID = "id";
    private static final String COLUMN_MESSAGE_SESSION_ID = "session_id";
    private static final String COLUMN_MESSAGE_ROLE = "role";
    private static final String COLUMN_MESSAGE_CONTENT = "content";
    private static final String COLUMN_MESSAGE_TIMESTAMP = "timestamp";
    private static final String COLUMN_MESSAGE_ANKI_NOTE_ID = "anki_note_id";

    // Prompt table
    private static final String TABLE_PROMPTS = "prompts";
    private static final String COLUMN_PROMPT_ID = "id";
    private static final String COLUMN_PROMPT_TITLE = "title";
    private static final String COLUMN_PROMPT_CONTENT = "content";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建会话表
        String createSessionsTable = "CREATE TABLE " + TABLE_SESSIONS + " (" +
                COLUMN_SESSION_ID + " TEXT PRIMARY KEY, " +
                COLUMN_SESSION_TITLE + " TEXT, " +
                COLUMN_SESSION_TIMESTAMP + " INTEGER)";
        db.execSQL(createSessionsTable);

        // 创建消息表
        String createMessagesTable = "CREATE TABLE " + TABLE_MESSAGES + " (" +
                COLUMN_MESSAGE_ID + " TEXT PRIMARY KEY, " +
                COLUMN_MESSAGE_SESSION_ID + " TEXT, " +
                COLUMN_MESSAGE_ROLE + " TEXT, " +
                COLUMN_MESSAGE_CONTENT + " TEXT, " +
                COLUMN_MESSAGE_TIMESTAMP + " INTEGER, " +
                COLUMN_MESSAGE_ANKI_NOTE_ID + " INTEGER, " +
                "FOREIGN KEY(" + COLUMN_MESSAGE_SESSION_ID + ") REFERENCES " +
                TABLE_SESSIONS + "(" + COLUMN_SESSION_ID + ") ON DELETE CASCADE)";
        db.execSQL(createMessagesTable);

        // Create prompts table
        db.execSQL("CREATE TABLE " + TABLE_PROMPTS + " (" +
            COLUMN_PROMPT_ID + " TEXT PRIMARY KEY," +
            COLUMN_PROMPT_TITLE + " TEXT NOT NULL," +
            COLUMN_PROMPT_CONTENT + " TEXT NOT NULL" +
            ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // 添加 anki_note_id 列
            try {
                db.execSQL("ALTER TABLE " + TABLE_MESSAGES + 
                    " ADD COLUMN " + COLUMN_MESSAGE_ANKI_NOTE_ID + " INTEGER");
            } catch (Exception e) {
                // 如果列已存在，忽略错误
                e.printStackTrace();
            }
        }
        
        // 如果有更多版本升级，可以继续添加条件
        // if (oldVersion < 3) { ... }
    }

    // 保存会话
    public void saveSession(Session session) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            // 保存会话信息
            ContentValues sessionValues = new ContentValues();
            sessionValues.put(COLUMN_SESSION_ID, session.getId());
            sessionValues.put(COLUMN_SESSION_TITLE, session.getTitle());
            sessionValues.put(COLUMN_SESSION_TIMESTAMP, SystemClock.elapsedRealtime());  // 更新时间戳
            db.insertWithOnConflict(TABLE_SESSIONS, null, sessionValues, SQLiteDatabase.CONFLICT_REPLACE);

            // 先删除该会话的所有旧消息
            db.delete(TABLE_MESSAGES, COLUMN_MESSAGE_SESSION_ID + "=?", 
                    new String[]{session.getId()});

            // 保存新的消息
            for (Message message : session.getMessages()) {
                ContentValues messageValues = new ContentValues();
                messageValues.put(COLUMN_MESSAGE_ID, message.getId());
                messageValues.put(COLUMN_MESSAGE_SESSION_ID, session.getId());
                messageValues.put(COLUMN_MESSAGE_ROLE, message.getRole());
                messageValues.put(COLUMN_MESSAGE_CONTENT, message.getContent());
                messageValues.put(COLUMN_MESSAGE_TIMESTAMP, SystemClock.elapsedRealtime());
                messageValues.put(COLUMN_MESSAGE_ANKI_NOTE_ID, message.getAnkiNoteId());
                db.insert(TABLE_MESSAGES, null, messageValues);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // 获取会话标题列表
    public List<SessionInfo> getSessionTitles() {
        List<SessionInfo> sessionInfos = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String[] columns = {
            COLUMN_SESSION_ID,
            COLUMN_SESSION_TITLE,
            COLUMN_SESSION_TIMESTAMP,
            "(SELECT COUNT(*) FROM " + TABLE_MESSAGES + 
            " WHERE " + COLUMN_MESSAGE_SESSION_ID + "=" + TABLE_SESSIONS + "." + COLUMN_SESSION_ID + ") as message_count"
        };
        
        Cursor cursor = db.query(TABLE_SESSIONS, columns, null, null, null, null, 
                COLUMN_SESSION_TIMESTAMP + " DESC");
        
        while (cursor.moveToNext()) {
            SessionInfo info = new SessionInfo(
                cursor.getString(cursor.getColumnIndex(COLUMN_SESSION_ID)),
                cursor.getString(cursor.getColumnIndex(COLUMN_SESSION_TITLE)),
                cursor.getLong(cursor.getColumnIndex(COLUMN_SESSION_TIMESTAMP)),
                cursor.getInt(cursor.getColumnIndex("message_count"))
            );
            sessionInfos.add(info);
        }
        cursor.close();
        return sessionInfos;
    }

    // 搜索会话（支持标题和内容的模糊查询）
    public List<SessionInfo> searchSessions(String query) {
        List<SessionInfo> sessionInfos = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        try {
            // 构建搜索SQL，使用子查询优化性能
            String sql = 
                "WITH MessageCounts AS (" +
                "   SELECT session_id, COUNT(*) as msg_count " +
                "   FROM " + TABLE_MESSAGES +
                "   GROUP BY session_id" +
                ") " +
                "SELECT DISTINCT s." + COLUMN_SESSION_ID + ", " +
                "s." + COLUMN_SESSION_TITLE + ", " +
                "s." + COLUMN_SESSION_TIMESTAMP + ", " +
                "COALESCE(mc.msg_count, 0) as message_count " +
                "FROM " + TABLE_SESSIONS + " s " +
                "LEFT JOIN MessageCounts mc ON s." + COLUMN_SESSION_ID + " = mc.session_id " +
                "LEFT JOIN " + TABLE_MESSAGES + " m ON s." + COLUMN_SESSION_ID + " = m." + COLUMN_MESSAGE_SESSION_ID + " " +
                "WHERE s." + COLUMN_SESSION_TITLE + " LIKE ? " +
                "   OR m." + COLUMN_MESSAGE_CONTENT + " LIKE ? " +
                "ORDER BY s." + COLUMN_SESSION_TIMESTAMP + " DESC";

            String[] selectionArgs = new String[]{"%" + query + "%", "%" + query + "%"};
            Cursor cursor = db.rawQuery(sql, selectionArgs);

            while (cursor.moveToNext()) {
                SessionInfo info = new SessionInfo(
                    cursor.getString(cursor.getColumnIndex(COLUMN_SESSION_ID)),
                    cursor.getString(cursor.getColumnIndex(COLUMN_SESSION_TITLE)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_SESSION_TIMESTAMP)),
                    cursor.getInt(cursor.getColumnIndex("message_count"))
                );
                // 避免重复
                if (!sessionInfos.stream().anyMatch(s -> s.id.equals(info.id))) {
                    sessionInfos.add(info);
                }
            }
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sessionInfos;
    }

    // 会话信息类
    public static class SessionInfo {
        public String id;
        public String title;
        public long timestamp;
        public int messageCount;

        public SessionInfo(String id, String title, long timestamp, int messageCount) {
            this.id = id;
            this.title = title;
            this.timestamp = timestamp;
            this.messageCount = messageCount;
        }
    }

    // 加载所有会话
    public List<Session> loadAllSessions() {
        List<Session> sessions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(TABLE_SESSIONS, null, null, null, null, null, 
                COLUMN_SESSION_TIMESTAMP + " DESC");
        
        while (cursor.moveToNext()) {
            String sessionId = cursor.getString(cursor.getColumnIndex(COLUMN_SESSION_ID));
            Session session = loadSession(sessionId);
            if (session != null) {
                sessions.add(session);
            }
        }
        cursor.close();
        return sessions;
    }

    // 加载单个会话
    public Session loadSession(String sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Session session = null;
        
        try {
            Cursor sessionCursor = db.query(TABLE_SESSIONS, null, 
                    COLUMN_SESSION_ID + "=?", new String[]{sessionId}, 
                    null, null, null);
            
            if (sessionCursor.moveToFirst()) {
                String title = sessionCursor.getString(sessionCursor.getColumnIndex(COLUMN_SESSION_TITLE));
                long timestamp = sessionCursor.getLong(sessionCursor.getColumnIndex(COLUMN_SESSION_TIMESTAMP));
                session = new Session(sessionId, title);
                session.setTimestamp(timestamp);  // 需要在 Session 类中添加 setTimestamp 方法
            }
            sessionCursor.close();

            if (session != null) {
                // 加载会话的消息，按时间戳排序
                Cursor messageCursor = db.query(TABLE_MESSAGES, 
                    new String[]{COLUMN_MESSAGE_ID, COLUMN_MESSAGE_ROLE, COLUMN_MESSAGE_CONTENT, COLUMN_MESSAGE_ANKI_NOTE_ID}, 
                    COLUMN_MESSAGE_SESSION_ID + "=?", 
                    new String[]{sessionId}, null, null, COLUMN_MESSAGE_TIMESTAMP + " ASC");

                while (messageCursor.moveToNext()) {
                    String messageId = messageCursor.getString(0);
                    String role = messageCursor.getString(1);
                    String content = messageCursor.getString(2);
                    Long ankiNoteId = messageCursor.isNull(3) ? null : messageCursor.getLong(3);
                    
                    Message message = new Message(role, content, "", messageId);
                    message.setAnkiNoteId(ankiNoteId);
                    session.addMessage(message);
                }
                messageCursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return session;
    }

    // 删除会话
    public boolean deleteSession(String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_SESSIONS, COLUMN_SESSION_ID + "=?", 
                new String[]{sessionId}) > 0;
    }

    // 添加更新消息 ankiNoteId 的方法
    public void updateMessageAnkiNoteId(String messageId, Long ankiNoteId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MESSAGE_ANKI_NOTE_ID, ankiNoteId);
        
        db.update(TABLE_MESSAGES, values, COLUMN_MESSAGE_ID + "=?", new String[]{messageId});
    }

    // Add methods for managing prompts
    public void savePrompt(Prompt prompt) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PROMPT_ID, prompt.getId());
        values.put(COLUMN_PROMPT_TITLE, prompt.getTitle());
        values.put(COLUMN_PROMPT_CONTENT, prompt.getContent());
        db.insertWithOnConflict(TABLE_PROMPTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<Prompt> getAllPrompts() {
        List<Prompt> prompts = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PROMPTS, null, null, null, null, null, null);
        
        while (cursor.moveToNext()) {
            Prompt prompt = new Prompt();
            prompt.setId(cursor.getString(cursor.getColumnIndex(COLUMN_PROMPT_ID)));
            prompt.setTitle(cursor.getString(cursor.getColumnIndex(COLUMN_PROMPT_TITLE)));
            prompt.setContent(cursor.getString(cursor.getColumnIndex(COLUMN_PROMPT_CONTENT)));
            prompts.add(0, prompt);
        }
        cursor.close();
        return prompts;
    }

    public void deletePrompt(String promptId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_PROMPTS, COLUMN_PROMPT_ID + "=?", new String[]{promptId});
    }
} 