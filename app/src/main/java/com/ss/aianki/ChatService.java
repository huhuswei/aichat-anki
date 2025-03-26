package com.ss.aianki;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import android.app.AlertDialog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.gson.Gson;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

import com.ichi2.anki.api.AddContentApi;

import java.util.Map;
import java.util.HashMap;
import android.content.SharedPreferences;
import java.util.Collections;

import retrofit2.Call;
import retrofit2.Response;
import okhttp3.ResponseBody;

public class ChatService {
    private final WebView webView;
    private OpenAiApi openAiApi;
    private final List<Message> messageHistory = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();
    private volatile String currentModel = "";
    private String outputFormat = "Markdown";
    private final Context context;
    private AddContentApi ankiApi;
    private Long lastSavedNoteId = null;  // 存储最近保存的笔记 ID
    private List<Session> sessionHistory = new ArrayList<>();
    private Session currentSession = null;
    private DatabaseHelper dbHelper;
    private volatile boolean isReceiving = false;  // 添加标志
    private BufferedReader currentReader = null;   // 添加当前reader引用
    private boolean isSingleTurnMode = false;
    private long selectedDeckId = -1; // Default to -1 (not selected)
    private AIServerConfig config = null;
    public ChatService(WebView webView, String apiKey, String baseUrl) {
        this.webView = webView;
        this.context = webView.getContext();
        this.config = new AIServerConfig();
        config.setApiKey(apiKey);
        config.setBaseUrl(baseUrl);
        this.openAiApi = createOpenAiApi(config.getApiKey(), config.getBaseUrl());
        
        // 初始化 AnkiDroid API
        this.ankiApi = new AddContentApi(context);
        this.dbHelper = new DatabaseHelper(context);
        
        // 加载所有会话
        sessionHistory.addAll(dbHelper.loadAllSessions());
        
        // 加载对话模式设置
        SharedPreferences prefs = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE);
        isSingleTurnMode = prefs.getBoolean("isSingleTurnMode", false);
    }

    public ChatService(WebView webView, AIServerConfig config) {
        this.webView = webView;
        this.context = webView.getContext();
        this.config = config;
        this.openAiApi = createOpenAiApi(this.config.getApiKey(), this.config.getBaseUrl());

        // 初始化 AnkiDroid API
        this.ankiApi = new AddContentApi(context);
        this.dbHelper = new DatabaseHelper(context);

        // 加载所有会话
        sessionHistory.addAll(dbHelper.loadAllSessions());

        // 加载对话模式设置
        SharedPreferences prefs = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE);
        isSingleTurnMode = prefs.getBoolean("isSingleTurnMode", false);
    }

    private OpenAiApi createOpenAiApi(String token, String baseUrl) {
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient client = defaultClient(token);
        
        // 添加日志拦截器
        OkHttpClient.Builder builder = client.newBuilder()
            .addInterceptor(chain -> {
                okhttp3.Request request = chain.request();
                System.out.println("Sending request to: " + request.url());
                System.out.println("Request headers: " + request.headers());
                okhttp3.Response response = chain.proceed(request);
                System.out.println("Response code: " + response.code());
                return response;
            });
        
        Retrofit retrofit = defaultRetrofit(builder.build(), mapper, baseUrl);
        return retrofit.create(OpenAiApi.class);
    }

    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        return mapper;
    }

    private static OkHttpClient defaultClient(String token) {
        return new OkHttpClient.Builder()
                .addInterceptor(new AuthenticationInterceptor(token))
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    private static Retrofit defaultRetrofit(OkHttpClient client, ObjectMapper mapper, String baseUrl) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
    }

    public synchronized void setModel(String model) {
        System.out.println("Setting model from: " + this.currentModel + " to: " + model);
        this.currentModel = model;
    }

    public synchronized String getCurrentModel() {
        return this.currentModel;
    }

    public void setOutputFormat(String format) {
        this.outputFormat = format;
    }

    @JavascriptInterface
    public void newSession() {
        // 如果当前有会话，先保存
        if (currentSession != null) {
            saveCurrentSession();
        }
        
        // 清空当前消息
        messageHistory.clear();
        // 通知 WebView 清空界面
        mainHandler.post(() -> {
            webView.evaluateJavascript("javascript:clearMessages();", null);
        });
        currentSession = null;
    }

    @JavascriptInterface
    public String getSessionList() {
        return gson.toJson(dbHelper.getSessionTitles());
    }

    @JavascriptInterface
    public void loadSession(String sessionId) {
        // 如果当前有会话，先保存
        if (currentSession != null) {
            saveCurrentSession();
        }
        
        Session session = dbHelper.loadSession(sessionId);
        if (session != null) {
            currentSession = session;
            messageHistory.clear();
            messageHistory.addAll(session.getMessages());
            
            // 打印加载的消息
            System.out.println("加载会话: " + sessionId);
            System.out.println("加载的消息列表:");
            for (Message msg : session.getMessages()) {
                System.out.println("ID: " + msg.getId() + ", Role: " + msg.getRole());
            }
            
            // 通知 WebView 重新加载消息，并保持消息ID
            mainHandler.post(() -> {
                webView.evaluateJavascript("javascript:reloadMessages(" + 
                    gson.toJson(session.getMessages()) + ");", null);
            });
            
            // 加载会话后，通知 JavaScript 更新 UI
            for (Message msg : currentSession.getMessages()) {
                String script = String.format(
                    "javascript:addMessageToUI(%s);",
                    gson.toJson(new MessageDTO(msg.getRole(), msg.getContent(), msg.getId(), msg.getAnkiNoteId()))
                );
                webView.evaluateJavascript(script, null);
            }
        }
    }

    @JavascriptInterface
    public synchronized void sendMessage(String content) {
        sendMessage(content, "");
    }

    @JavascriptInterface
    public synchronized void sendMessage(String content, String prompt) {
        if (isReceiving) {
            // If already receiving a message, don't send a new one
            mainHandler.post(() -> {
                Toast.makeText(context, "正在接收回复，请稍候...", Toast.LENGTH_SHORT).show();
            });
            return;
        }
        
        // Set the receiving flag
        isReceiving = true;
        
        String model = getCurrentModel();
        System.out.println("Sending message with model: " + model);
        if (model.isEmpty()) {
            mainHandler.post(() -> {
                Toast.makeText(context, "请先选择模型", Toast.LENGTH_SHORT).show();
            });
            return;
        }
        
        // 如果是单轮对话模式，每次发送消息都创建新会话
        if (isSingleTurnMode) {
            // 如果有当前会话，先保存
            if (currentSession != null) {
                saveCurrentSession();
            }
            
            // 创建新会话
            currentSession = new Session(content);
            sessionHistory.add(0, currentSession);
            dbHelper.saveSession(currentSession);
            
            // 清空消息历史，准备新的对话
            messageHistory.clear();
            
            // 通知 WebView 清空界面
            mainHandler.post(() -> {
                webView.evaluateJavascript("javascript:clearMessages();", null);
            });
        }
        
        System.out.println("Sending message: " + content);
        System.out.println("Current model (synchronized): " + model);
        
        Message userMsg = new Message("user", content, prompt);
        
        // 如果是新会话，创建会话
        if (currentSession == null) {
            currentSession = new Session(content);
            sessionHistory.add(0, currentSession);
        }
        
        // 添加消息到当前会话
        currentSession.addMessage(userMsg);
        messageHistory.add(userMsg);
        notifyWebViewNewMessage(userMsg);
        
        final Message assistantMsg = new Message("assistant", "");
        currentSession.addMessage(assistantMsg);
        messageHistory.add(assistantMsg);
        notifyWebViewNewMessage(assistantMsg);
        
        // 保存用户消息
        saveCurrentSession();
        
        executorService.execute(() -> {
            try {
                isReceiving = true;  // 设置标志
                // 转换消息格式
                List<ChatMessage> chatMessages = new ArrayList<>();
                for (Message msg : messageHistory) {
                    chatMessages.add(new ChatMessage(msg.getRole(), msg.getPrompt().isEmpty() ? msg.getContent() : msg.getPrompt() + msg.getContent()));
                }

                // 确保使用当前选择的模型
                if (getCurrentModel().isEmpty()) {
                    throw new IllegalStateException("No model selected");
                }

                // 构建请求
                ChatCompletionRequest request = ChatCompletionRequest.builder()
                        .model(getCurrentModel())
                        .temperature(Double.valueOf(config.getTemperature()))
                        .messages(chatMessages)
                        .stream(true)
                        .build();

                // 打印请求信息
                System.out.println("Sending request with model: " + getCurrentModel());
                System.out.println("Full request: " + gson.toJson(request));

                openAiApi.streamChatCompletion(request)
                        .subscribe(
                            responseBody -> {
                                try {
                                    BufferedReader reader = new BufferedReader(
                                        new InputStreamReader(responseBody.byteStream())
                                    );
                                    currentReader = reader;  // 保存当前reader引用
                                    String line;
                                    while (!Thread.currentThread().isInterrupted() 
                                            && (line = reader.readLine()) != null) {
                                        System.out.println("Raw line: " + line);
                                        handleStreamResponse(line);
                                    }
                                } catch (Exception e) {
                                    if (!Thread.currentThread().isInterrupted()) {
                                        System.out.println("Error processing response: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                } finally {
                                    currentReader = null;
                                    isReceiving = false;  // 重置标志
                                }
                            },
                            error -> {
                                System.out.println("Error: " + error.getMessage());
                                error.printStackTrace();
                                mainHandler.post(() -> {
                                    assistantMsg.setContent("发生错误: " + error.getMessage());
                                    updateAssistantMessage(assistantMsg);
                                });
                                isReceiving = false;  // 重置标志
                            }
                        );
            } catch (Exception e) {
                System.out.println("Error sending message: " + e.getMessage());
                e.printStackTrace();
                mainHandler.post(() -> {
                    assistantMsg.setContent("发生错误: " + e.getMessage());
                    updateAssistantMessage(assistantMsg);
                });
                isReceiving = false;  // 重置标志
            }
        });
    }

    @JavascriptInterface
    public String getMessageHistory() {
        return gson.toJson(messageHistory);
    }
    
    private void notifyWebViewNewMessage(Message message) {
        mainHandler.post(() -> {
            System.out.println("发送新消息到 WebView: ID=" + message.getId() + ", Role=" + message.getRole());
            String script = String.format(
                "javascript:console.log('Adding message:', '%s'); addMessage(%s);",
                message.getId(),
                gson.toJson(message)
            );
            webView.evaluateJavascript(script, null);
        });
    }
    
    private String formatContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        switch (outputFormat) {
            case "HTML":
                // 如果是 HTML 格式，需要移除 Markdown 标记
                return content.replaceAll("```[\\s\\S]*?```", "")  // 移除代码块
                            .replaceAll("`([^`]+)`", "$1")         // 移除行内代码
                            .replaceAll("\\*\\*([^*]+)\\*\\*", "$1") // 移除粗体
                            .replaceAll("\\*([^*]+)\\*", "$1")     // 移除斜体
                            .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1"); // 移除链接，保留文本
            case "Markdown":
            default:
                return content;
        }
    }

    private void updateAssistantMessage(Message message) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> updateAssistantMessage(message));
            return;
        }

        String formattedContent = formatContent(message.getContent());
        System.out.println("Updating message: " + message.getId());
        System.out.println("Content: " + message.getContent());
        System.out.println("Formatted content: " + formattedContent);
        
        String updateScript = String.format(
            "javascript:updateMessage(%s);",
            gson.toJson(message)
        );
        webView.evaluateJavascript(updateScript, null);
    }

    @JavascriptInterface
    public String getOutputFormat() {
        return outputFormat;
    }

    @JavascriptInterface
    public boolean deleteLastNote() {
        if (lastSavedNoteId == null) {
            Toast.makeText(context, "没有可删除的笔记", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            // 检查是否安装了AnkiDroid
            if (!AnkiDroidHelper.isAnkiDroidInstalled(context)) {
                Toast.makeText(context, "请先安装 AnkiDroid", Toast.LENGTH_LONG).show();
                return false;
            }

            // 检查权限
            if (!AnkiDroidHelper.hasPermission(context)) {
                Toast.makeText(context, "请授予 AnkiDroid 权限", Toast.LENGTH_LONG).show();
                return false;
            }

            // 删除笔记
            if (AnkiDroidHelper.deleteNote(context, lastSavedNoteId)) {
                Toast.makeText(context, "已删除笔记", Toast.LENGTH_SHORT).show();
                lastSavedNoteId = null;
                return true;
            } else {
                Toast.makeText(context, "删除笔记失败", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "删除笔记失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @JavascriptInterface
    public boolean saveToAnki(String messageId) {
        try {
            // Find the message
            Message targetMessage = null;
            for (Message msg : messageHistory) {
                if (messageId.equals(msg.getId())) {
                    targetMessage = msg;
                    break;
                }
            }
            
            if (targetMessage == null) {
                System.out.println("未找到消息: " + messageId);
                return false;
            }
            
            // Set answer content
            String answer = targetMessage.getContent();
            
            // Check if AnkiDroid is installed
            if (!AnkiDroidHelper.isAnkiDroidInstalled(context)) {
                mainHandler.post(() -> {
                    Toast.makeText(context, "请先安装 AnkiDroid", Toast.LENGTH_LONG).show();
                    AnkiDroidHelper.openPlayStore(context, "com.ichi2.anki");
                });
                return false;
            }
            
            // Check permissions
            if (!AnkiDroidHelper.hasPermission(context)) {
                mainHandler.post(() -> {
                    Toast.makeText(context, "需要 AnkiDroid 权限", Toast.LENGTH_LONG).show();
                });
                return false;
            }
            
            // From here, start trying to create deck and model
            try {
                // Get or create deck - use selected deck if available, otherwise use "AI Chat"
                long deckId;
                if (selectedDeckId != -1) {
                    // Use selected deck
                    deckId = selectedDeckId;
                } else {
                    // Use default "AI Chat" deck
                    deckId = getOrCreateDeck("AI Chat");
                    if (deckId == -1) {
                        return false;
                    }
                }
                
                // Continue with existing code...
                System.out.println("尝试保存消息ID: " + messageId);
                System.out.println("当前消息历史大小: " + messageHistory.size());
                
                // Print all message IDs and roles for debugging
                System.out.println("消息历史列表:");
                for (Message msg : messageHistory) {
                    System.out.println("ID: " + msg.getId() + ", Role: " + msg.getRole() + ", Content长度: " + 
                        (msg.getContent() != null ? msg.getContent().length() : 0));
                }

                String question = "";

                // Find the most recent user message before the target message
                int targetIndex = messageHistory.indexOf(targetMessage);
                for (int i = targetIndex - 1; i >= 0; i--) {
                    Message msg = messageHistory.get(i);
                    System.out.println("向前查找用户消息: " + msg.getId() + ", 角色: " + msg.getRole());
                    if ("user".equals(msg.getRole())) {
                        question = msg.getContent();
                        break;
                    }
                }

                System.out.println("找到问题: " + question);
                System.out.println("找到答案: " + answer);

                if (question.isEmpty() || answer.isEmpty()) {
                    Toast.makeText(context, "未找到完整的问答对", Toast.LENGTH_SHORT).show();
                    System.out.println("未找到完整的问答对: question=" + question + ", answer=" + answer);
                    return false;
                }

                // 获取或创建模型
                Map<Long, String> models = null;
                try {
                    models = ankiApi.getModelList();
                } catch (Exception e) {
                    System.out.println("获取模型列表失败: " + e.getMessage());
                    e.printStackTrace();
                }
                
                if (models == null) {
                    models = new HashMap<>(); // 创建空Map避免空指针异常
                }
                
                long modelId = -1;
                for (Map.Entry<Long, String> model : models.entrySet()) {
                    if ("AI Chat".equals(model.getValue())) {
                        modelId = model.getKey();
                        break;
                    }
                }

                // 如果模型不存在，创建新模型
                if (modelId == -1) {
                    try {
                        String[] fieldNames = {"Question", "Answer"};
                        String[] cardNames = {"AI Chat Card"};
                        
                        // 从资源文件读取模板
                        String templateContent = "";
                        try {
                            InputStream is = context.getAssets().open("anki_template.html");
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            reader.close();
                            templateContent = sb.toString();
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(context, "读取模板文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        
                        // 使用 "@@@@@" 分隔符分割模板内容
                        String[] parts = templateContent.split("@@@@@");
                        
                        // 确保有足够的部分
                        if (parts.length < 3) {
                            Toast.makeText(context, "模板文件格式错误", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        
                        // 分配到相应的变量
                        String questionFormat = parts[0].trim();
                        String answerFormat = parts[1].trim();
                        String css = parts[2].trim();
                        
                        String[] questionFormats = {questionFormat};
                        String[] answerFormats = {answerFormat};
                        
                        modelId = ankiApi.addNewCustomModel(
                            "AI Chat",
                            fieldNames,
                            cardNames,
                            questionFormats,
                            answerFormats,
                            css,
                            deckId,
                            null
                        );
                    } catch (Exception e) {
                        System.out.println("创建模型失败: " + e.getMessage());
                        e.printStackTrace();
                        Toast.makeText(context, "创建模型失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }

                // 在保存笔记之前，处理 Markdown 和 LaTeX
                String processedQuestion = question;
                String processedAnswer = answer;

                // 保存到 AnkiDroid
                long noteId = ankiApi.addNote(modelId, deckId, 
                    new String[]{processedQuestion, processedAnswer}, null);

                if (noteId > 0) {
                    Toast.makeText(context, "已保存到Anki", Toast.LENGTH_SHORT).show();
                    lastSavedNoteId = noteId;
                    
                    // 保存 noteId 到消息对象
                    for (Message msg : messageHistory) {
                        if (messageId.equals(msg.getId())) {
                            msg.setAnkiNoteId(noteId);
                            // 保存到数据库
                            dbHelper.updateMessageAnkiNoteId(msg.getId(), noteId);
                            break;
                        }
                    }
                    
                    return true;
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show();
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("保存失败: " + e.getMessage());
                System.out.println("异常堆栈: ");
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(context, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("保存失败: " + e.getMessage());
            System.out.println("异常堆栈: ");
            e.printStackTrace();
            mainHandler.post(() -> {
                Toast.makeText(context, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
            return false;
        }
    }

    @JavascriptInterface
    public void openAnkiDroid() {
        try {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName("com.ichi2.anki", "com.ichi2.anki.DeckPicker"));
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "打开 AnkiDroid 失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @JavascriptInterface
    public String searchSessions(String query) {
        return gson.toJson(dbHelper.searchSessions(query));
    }

    @JavascriptInterface
    public boolean deleteSession(String sessionId) {
        boolean success = dbHelper.deleteSession(sessionId);
        if (success) {
            sessionHistory.removeIf(session -> session.getId().equals(sessionId));
            if (currentSession != null && currentSession.getId().equals(sessionId)) {
                currentSession = null;
                messageHistory.clear();
                mainHandler.post(() -> {
                    webView.evaluateJavascript("javascript:clearMessages();", null);
                });
            }
        }
        return success;
    }

    // 在每次收到助手回复时保存会话
    private void handleAssistantResponse(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            if (json != null && json.has("choices")) {
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices != null && !choices.isJsonNull() && choices.size() > 0) {
                    JsonElement choiceElement = choices.get(0);
                    if (!choiceElement.isJsonNull() && choiceElement.isJsonObject()) {
                        JsonObject choice = choiceElement.getAsJsonObject();
                        if (choice.has("delta")) {
                            JsonElement deltaElement = choice.get("delta");
                            if (!deltaElement.isJsonNull() && deltaElement.isJsonObject()) {
                                JsonObject delta = deltaElement.getAsJsonObject();
                                if (delta.has("content")) {
                                    JsonElement contentElement = delta.get("content");
                                    if (!contentElement.isJsonNull()) {
                                        String deltaContent = contentElement.getAsString();
                                        Message lastMessage = messageHistory.get(messageHistory.size() - 1);
                                        lastMessage.setContent(lastMessage.getContent() + deltaContent);
                                        
                                        // 在主线程中更新 UI
                                        mainHandler.post(() -> {
                                            updateAssistantMessage(lastMessage);
                                        });
                                        
                                        // 保存当前会话
                                        saveCurrentSession();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("JSON 处理错误: " + e.getMessage());
            System.out.println("原始内容: " + content);
            
            // 在主线程中显示错误
            mainHandler.post(() -> {
                if (!messageHistory.isEmpty()) {
                    Message lastMessage = messageHistory.get(messageHistory.size() - 1);
                    lastMessage.setContent("处理响应时发生错误: " + e.getMessage());
                    updateAssistantMessage(lastMessage);
                }
            });
        }
    }

    // 修改保存会话的方法，添加更多错误处理
    private void saveCurrentSession() {
        if (currentSession != null) {
            try {
                // 更新会话内容
                currentSession.getMessages().clear();
                currentSession.getMessages().addAll(messageHistory);
                // 保存到数据库
                dbHelper.saveSession(currentSession);
                System.out.println("会话已保存: " + currentSession.getId() + ", 消息数: " + currentSession.getMessages().size());
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("保存会话失败: " + e.getMessage());
                // 在主线程中显示错误
                mainHandler.post(() -> {
                    Toast.makeText(context, "保存会话失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    // 修改处理流式响应的部分
    private void handleStreamResponse(String line) {
        if (line.startsWith("data: ")) {
            String data = line.substring(6).trim();
            if (!"[DONE]".equals(data)) {
                try {
                    JsonObject response = new JsonParser().parse(data).getAsJsonObject();
                    JsonArray choices = response.getAsJsonArray("choices");
                    if (choices != null && choices.size() > 0) {
                        JsonObject choice = choices.get(0).getAsJsonObject();
                        JsonObject delta = choice.getAsJsonObject("delta");
                        if (delta != null) {
                            // 检查 content 字段是否存在且不为 null
                            JsonElement contentElement = delta.get("content");
                            if (contentElement != null && !contentElement.isJsonNull()) {
                                String content = contentElement.getAsString();
                                updateAssistantMessageStream(content);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error parsing response: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // Stream response is complete
                mainHandler.post(() -> {
                    saveCurrentSession();
                    // Notify JavaScript response is complete
                    webView.evaluateJavascript("javascript:onResponseComplete();", null);
                });
            }
        }
    }

    // 添加新方法处理流式更新
    private void updateAssistantMessageStream(String deltaContent) {
        mainHandler.post(() -> {
            Message lastMessage = messageHistory.get(messageHistory.size() - 1);
            // 追加新内容
            String currentContent = lastMessage.getContent();
            String newContent = currentContent + deltaContent;
            lastMessage.setContent(newContent);
            
            // 立即更新 WebView 显示
            String script = String.format(
                "javascript:updateMessageContent('%s', %s);",
                lastMessage.getId(),
                gson.toJson(formatContent(newContent))
            );
            webView.evaluateJavascript(script, null);
        });
    }

    // 添加一个内部类来表示会话信息
    private static class SessionInfo {
        private String id;
        private String title;
        private long timestamp;

        public SessionInfo(String id, String title, long timestamp) {
            this.id = id;
            this.title = title;
            this.timestamp = timestamp;
        }
    }

    @JavascriptInterface
    public boolean undoAnkiSave(String messageId) {
        try {
            // 查找消息
            for (Message msg : messageHistory) {
                if (messageId.equals(msg.getId())) {
                    Long noteId = msg.getAnkiNoteId();
                    if (noteId != null) {
                        // 删除笔记
                        if (AnkiDroidHelper.deleteNote(context, noteId)) {
                            Toast.makeText(context, "已删除笔记", Toast.LENGTH_SHORT).show();
                            // 清除 noteId
                            msg.setAnkiNoteId(null);
                            // 更新数据库
                            dbHelper.updateMessageAnkiNoteId(msg.getId(), null);
                            return true;
                        }
                    }
                    break;
                }
            }
            
            mainHandler.post(() -> {
                Toast.makeText(context, "未找到对应的 Anki 笔记", Toast.LENGTH_SHORT).show();
            });
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("删除失败: " + e.getMessage());
            mainHandler.post(() -> {
                Toast.makeText(context, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
            return false;
        }
    }

    @JavascriptInterface
    public void updateMessageContent(String messageId, String newContent) {
        try {
            // 查找并更新消息内容
            for (Message msg : messageHistory) {
                if (messageId.equals(msg.getId())) {
                    msg.setContent(newContent);
                    break;
                }
            }
            
            // 保存当前会话
            saveCurrentSession();
            
            mainHandler.post(() -> {
                Toast.makeText(context, "修改已保存", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            e.printStackTrace();
            mainHandler.post(() -> {
                Toast.makeText(context, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    public synchronized void updateConfig(String apiKey, String baseUrl) {
        System.out.println("Updating ChatService config");
        this.openAiApi = createOpenAiApi(apiKey, baseUrl);
    }

    // 添加中断当前响应的方法
    private void interruptCurrentResponse() {
        if (currentReader != null) {
            try {
                currentReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            currentReader = null;
        }
        isReceiving = false;
        System.out.println("Response interrupted");
    }

    // 添加检查是否正在接收响应的方法
    @JavascriptInterface
    public boolean isReceivingResponse() {
        return isReceiving;
    }

    @JavascriptInterface
    public String getMessageContent(String messageId) {
        for (Message msg : messageHistory) {
            if (messageId.equals(msg.getId())) {
                return msg.getContent();
            }
        }
        return "";
    }

    /**
     * 更新Anki模板
     * @return 是否更新成功
     */
    public boolean updateAnkiTemplate() {
        try {
            // Check if AnkiDroid is installed
            if (!AnkiDroidHelper.isAnkiDroidInstalled(context)) {
                mainHandler.post(() -> {
                    Toast.makeText(context, "请先安装 AnkiDroid", Toast.LENGTH_LONG).show();
                    AnkiDroidHelper.openPlayStore(context, "com.ichi2.anki");
                });
                return false;
            }
            
            // Check permissions
            if (!AnkiDroidHelper.hasPermission(context)) {
                mainHandler.post(() -> {
                    Toast.makeText(context, "需要 AnkiDroid 权限", Toast.LENGTH_LONG).show();
                });
                return false;
            }
            
            // Get or create deck - use selected deck if available, otherwise use "AI Chat"
            long deckId;
            if (selectedDeckId != -1) {
                // Use selected deck
                deckId = selectedDeckId;
            } else {
                // Use default "AI Chat" deck
                deckId = getOrCreateDeck("AI Chat");
                if (deckId == -1) {
                    return false;
                }
            }
            
            // 获取模型列表
            Map<Long, String> models = null;
            try {
                models = ankiApi.getModelList();
            } catch (Exception e) {
                System.out.println("获取模型列表失败: " + e.getMessage());
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(context, "获取模型列表失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                return false;
            }
            
            // 查找 AI Chat 模型
            long oldModelId = -1;
            for (Map.Entry<Long, String> model : models.entrySet()) {
                if ("AI Chat".equals(model.getValue())) {
                    oldModelId = model.getKey();
                    break;
                }
            }
            
            // 从资源文件读取模板
            String templateContent = "";
            try {
                InputStream is = context.getAssets().open("anki_template.html");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                templateContent = sb.toString();
            } catch (IOException e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(context, "读取模板文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                return false;
            }
            
            // 使用 "@@@@@" 分隔符分割模板内容
            String[] parts = templateContent.split("@@@@@");
            
            // 确保有足够的部分
            if (parts.length < 3) {
                mainHandler.post(() -> {
                    Toast.makeText(context, "模板文件格式错误", Toast.LENGTH_SHORT).show();
                });
                return false;
            }
            
            // 分配到相应的变量
            String questionFormat = parts[0].trim();
            String answerFormat = parts[1].trim();
            String css = parts[2].trim();
            
            String[] fieldNames = {"Question", "Answer"};
            String[] cardNames = {"AI Chat Card"};
            String[] questionFormats = {questionFormat};
            String[] answerFormats = {answerFormat};
            
            // 如果找到了旧模型，先尝试删除它
            if (oldModelId != -1) {
                try {
                    // 获取使用该模型的所有笔记
                    List<Long> noteIds = new ArrayList<>();
                    
                    // 使用 AnkiDroid API 查找使用该模型的笔记
                    // 注意：这里需要使用 AnkiDroid 的内部 API，可能需要特殊权限
                    // 这里我们采用一个变通方法：创建一个新模型，然后将所有笔记迁移到新模型
                    
//                    // 创建一个临时模型名称，确保不会与现有模型冲突
//                    String tempModelName = "AI Chat Temp " + System.currentTimeMillis();
//
//                    // 创建新模型
//                    long newModelId = ankiApi.addNewCustomModel(
//                        tempModelName,
//                        fieldNames,
//                        cardNames,
//                        questionFormats,
//                        answerFormats,
//                        css,
//                        deckId,
//                        null
//                    );
//
//                    System.out.println("创建临时模型成功，ID: " + newModelId);
                    
                    // 现在创建最终的模型
                    long finalModelId = ankiApi.addNewCustomModel(
                        "AI Chat",
                        fieldNames,
                        cardNames,
                        questionFormats,
                        answerFormats,
                        css,
                        deckId,
                        null
                    );
                    
                    System.out.println("创建或更新模型成功，ID: " + finalModelId);
                    
                    // 通知用户模板已更新
                    mainHandler.post(() -> {
                        Toast.makeText(context, "Anki模板已更新，新卡片将使用新模板", Toast.LENGTH_LONG).show();
                    });
                    
                    return true;
                } catch (Exception e) {
                    System.out.println("更新模型失败: " + e.getMessage());
                    e.printStackTrace();
                    mainHandler.post(() -> {
                        Toast.makeText(context, "更新模型失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                    return false;
                }
            } else {
                // 如果没有找到旧模型，直接创建新模型
                try {
                    long modelId = ankiApi.addNewCustomModel(
                        "AI Chat",
                        fieldNames,
                        cardNames,
                        questionFormats,
                        answerFormats,
                        css,
                        deckId,
                        null
                    );
                    System.out.println("创建新模型成功，ID: " + modelId);
                    return true;
                } catch (Exception e) {
                    System.out.println("创建模型失败: " + e.getMessage());
                    e.printStackTrace();
                    mainHandler.post(() -> {
                        Toast.makeText(context, "创建模型失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                    return false;
                }
            }
        } catch (Exception e) {
            System.out.println("更新模板失败: " + e.getMessage());
            e.printStackTrace();
            mainHandler.post(() -> {
                Toast.makeText(context, "更新模板失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
            return false;
        }
    }

    @JavascriptInterface
    public void setConversationMode(String mode) {
        boolean newMode = "single".equals(mode);
        if (newMode != isSingleTurnMode) {
            isSingleTurnMode = newMode;
            // 保存设置到 SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("isSingleTurnMode", isSingleTurnMode).apply();
            
            // 不再清除消息历史，只保存当前会话
            if (isSingleTurnMode && currentSession != null) {
                // 保存当前会话
                saveCurrentSession();
            }
        }
    }

    @JavascriptInterface
    public String getConversationMode() {
        return isSingleTurnMode ? "single" : "multi";
    }

    @JavascriptInterface
    public void appendPrompt(String promptContent) {
        mainHandler.post(() -> {
            WebView webView = this.webView;
            if (webView != null) {
//                webView.evaluateJavascript(
//                        String.format("javascript:(function() {" +
//                                "let input = document.getElementById('message-input');" +
//                                "input.value = `%s`;" +
//                                "input.style.height = 'auto';" +
//                                "input.style.height = Math.min(Math.max(input.scrollHeight, 40), parseInt(getComputedStyle(input).lineHeight) * 5) + 'px';" +
//                                "input.focus();" +  // Focus the input
//                                "input.setSelectionRange(input.value.length, input.value.length);" +  // Set cursor to end
//                                "})();", promptContent.replace("`", "\\`")),
//                        null
//                );

                webView.evaluateJavascript(
                        String.format("javascript:showMessage(`%s`);", promptContent.replace("`", "\\`")),
                        null
                );
            }
        });
    }

    /**
     * Set the selected Anki deck ID
     * @param deckId The deck ID to use for saving cards
     */
    public void setSelectedDeckId(long deckId) {
        this.selectedDeckId = deckId;
    }
    
    /**
     * Get the currently selected deck ID
     * @return The selected deck ID or -1 if none selected
     */
    public long getSelectedDeckId() {
        return selectedDeckId;
    }

    /**
     * Get all available Anki decks, sorted by name
     * @return JSON string of deck list, or empty array if failed
     */
    @JavascriptInterface
    public String getAnkiDecks() {
        Map<Long, String> decks = getAnkiDeckList();
        if (decks != null) {
            // Convert to a list of maps for easier JSON serialization
            List<Map<String, Object>> deckList = new ArrayList<>();
            for (Map.Entry<Long, String> entry : decks.entrySet()) {
                Map<String, Object> deck = new HashMap<>();
                deck.put("id", entry.getKey());
                deck.put("name", entry.getValue());
                deckList.add(deck);
            }
            
            // Sort the list by deck name
            Collections.sort(deckList, (deck1, deck2) -> {
                String name1 = (String) deck1.get("name");
                String name2 = (String) deck2.get("name");
                return name1.compareToIgnoreCase(name2);
            });
            
            return gson.toJson(deckList);
        }
        return "[]";
    }

    /**
     * Helper method to get Anki deck list
     * @return Map of deck IDs to deck names, or null if failed
     */
    private Map<Long, String> getAnkiDeckList() {
        // Check if AnkiDroid is installed
        if (!AnkiDroidHelper.isAnkiDroidInstalled(context)) {
            mainHandler.post(() -> {
                Toast.makeText(context, "请先安装 AnkiDroid", Toast.LENGTH_LONG).show();
                AnkiDroidHelper.openPlayStore(context, "com.ichi2.anki");
            });
            return null;
        }
        
        // Check permissions
        if (!AnkiDroidHelper.hasPermission(context)) {
            mainHandler.post(() -> {
                Toast.makeText(context, "需要 AnkiDroid 权限", Toast.LENGTH_LONG).show();
            });
            return null;
        }
        
        // Get deck list
        Map<Long, String> decks = null;
        try {
            decks = ankiApi.getDeckList();
            System.out.println("成功获取牌组列表: " + (decks != null ? decks.size() : "null"));
        } catch (Exception e) {
            System.out.println("获取牌组列表失败: " + e.getMessage());
            e.printStackTrace();
            mainHandler.post(() -> {
                Toast.makeText(context, "获取牌组列表失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
            return null;
        }
        
        if (decks == null) {
            System.out.println("牌组列表为空，可能是权限问题");
            mainHandler.post(() -> {
                Toast.makeText(context, "无法访问 AnkiDroid 牌组，请检查权限", Toast.LENGTH_LONG).show();
            });
            return null;
        }
        
        return decks;
    }

    /**
     * Get or create a deck with the given name
     * @param deckName The name of the deck to get or create
     * @return The deck ID, or -1 if failed
     */
    private long getOrCreateDeck(String deckName) {
        Map<Long, String> decks = getAnkiDeckList();
        if (decks == null) {
            return -1;
        }
        
        // Look for existing deck
        for (Map.Entry<Long, String> deck : decks.entrySet()) {
            if (deckName.equals(deck.getValue())) {
                return deck.getKey();
            }
        }
        
        // Create new deck if not found
        try {
            long deckId = ankiApi.addNewDeck(deckName);
            System.out.println("创建新牌组 " + deckName + "，ID: " + deckId);
            return deckId;
        } catch (Exception e) {
            System.out.println("创建牌组失败: " + e.getMessage());
            e.printStackTrace();
            mainHandler.post(() -> {
                Toast.makeText(context, "创建牌组失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
            return -1;
        }
    }

    /**
     * Interrupt the current response generation
     */
    @JavascriptInterface
    public void interruptResponse() {
        if (isReceiving) {
            interruptCurrentResponse();
            
            // Notify the user that the response was interrupted
            mainHandler.post(() -> {
                if (!messageHistory.isEmpty()) {
                    Message lastMessage = messageHistory.get(messageHistory.size() - 1);
                    if ("assistant".equals(lastMessage.getRole())) {
                        String currentContent = lastMessage.getContent();
                        String newContent = currentContent + "\n\n[已停止生成]";
                        lastMessage.setContent(newContent);
                        
                        // Update the message in the database using the existing method
                        if (currentSession != null) {
                            saveCurrentSession();
                        }
                        
                        // Update the UI
                        String script = "updateMessageContent('" + lastMessage.getId() + "', " + 
                                        gson.toJson(newContent) + ");";
                        webView.evaluateJavascript(script, null);
                    }
                }
            });
        }
    }

    private void handleStreamingResponse(Call<ResponseBody> call, String messageId) {
        try {
            Response<ResponseBody> response = call.execute();
            if (!response.isSuccessful() || response.body() == null) {
                // Handle error...
                isReceiving = false;  // Clear the flag on error
                
                // Notify JavaScript that generation is complete
                mainHandler.post(() -> {
                    webView.evaluateJavascript("javascript:onResponseComplete();", null);
                });
                return;
            }

            InputStream inputStream = response.body().byteStream();
            currentReader = new BufferedReader(new InputStreamReader(inputStream));
            
            String line;
            while ((line = currentReader.readLine()) != null) {
                // Process the line...
            }
            
            // Clear the flag when done
            isReceiving = false;
            currentReader = null;
            
            // Notify JavaScript that generation is complete
            mainHandler.post(() -> {
                webView.evaluateJavascript("javascript:onResponseComplete();", null);
            });
            
        } catch (Exception e) {
            // Handle exception...
            isReceiving = false;  // Clear the flag on exception
            currentReader = null;
            
            // Notify JavaScript that generation is complete
            mainHandler.post(() -> {
                webView.evaluateJavascript("javascript:onResponseComplete();", null);
            });
        }
    }
} 