package com.ss.aianki;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.gson.Gson;
import com.ichi2.anki.FlashCardsContract;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;

import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

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

import io.reactivex.disposables.Disposable;

public class ChatService {
    private static String AI_CHAT = "AI Chat";
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
    AnkiDroidHelper mAnkidroid;
    private final ContentResolver mResolver;
    private Long lastSavedNoteId = null;  // 存储最近保存的笔记 ID
    private List<Session> sessionHistory = new ArrayList<>();
    private Session currentSession = null;
    private DatabaseHelper dbHelper;
    private volatile boolean isReceiving = false;  // 添加标志
    private BufferedReader currentReader = null;   // 添加当前reader引用
    private boolean isSingleTurnMode = false;
    private Disposable currentSubscription = null;  // 保存当前订阅
    private ResponseBody currentResponseBody = null;  // 保存当前响应体
    private long selectedDeckId = -1; // Default to -1 (not selected)
    private AIServerConfig config = null;
    // 保存当前 Call 对象以便取消
    private volatile okhttp3.Call currentCall = null;
    // 添加一个用于中断的 volatile 标志
    private volatile boolean interruptRequested = false;
    public ChatService(Context aContext, WebView webView, String apiKey, String baseUrl) {
        this.webView = webView;
        this.context = aContext;
        this.config = new AIServerConfig();
        mAnkidroid = MyApplication.getAnkiDroid();
        mResolver = context.getContentResolver();
        config.setApiKey(apiKey);
        config.setBaseUrl(baseUrl);
        this.openAiApi = createOpenAiApi(config.getApiKey(), config.getBaseUrl());
        
        // 初始化 AnkiDroid API
        this.ankiApi = new AddContentApi(context);
        this.dbHelper = new DatabaseHelper(context);
        selectedDeckId = Settings.getInstance(aContext).get(Settings.CURRENT_DECK_ID, -1L);
        
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
        mAnkidroid = MyApplication.getAnkiDroid();
        mResolver = context.getContentResolver();
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
//                System.out.println("Sending request to: " + request.url());
//                System.out.println("Request headers: " + request.headers());
                okhttp3.Response response = chain.proceed(request);
//                System.out.println("Response code: " + response.code());
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
//        System.out.println("Setting model from: " + this.currentModel + " to: " + model);
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
//            System.out.println("加载会话: " + sessionId);
//            System.out.println("加载的消息列表:");
//            for (Message msg : session.getMessages()) {
//                System.out.println("ID: " + msg.getId() + ", Role: " + msg.getRole());
//            }
            
            // 通知 WebView 重新加载消息，并保持消息ID
            mainHandler.post(() -> {
                webView.evaluateJavascript("javascript:reloadMessages(" + 
                    gson.toJson(session.getMessages()) + ");", null);
            });
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

        // 确保清理之前的连接状态
        cleanupCurrentConnection();

        // Set the receiving flag
        isReceiving = true;
        interruptRequested = false;  // 重置中断标志
        
        String model = getCurrentModel();
//        System.out.println("Sending message with model: " + model);
        if (model.isEmpty()) {
            mainHandler.post(() -> {
                Toast.makeText(context, "请先选择模型", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        // 解析是否包含文件附件
        String textContent = content;
        try {
            JsonObject json = new JsonParser().parse(content).getAsJsonObject();
            if (json.has("text") && json.has("files")) {
                textContent = json.get("text").getAsString();
            }
        } catch (Exception ignored) {}
        final String finalTextContent = textContent;

        // 如果是单轮对话模式，每次发送消息都创建新会话
        if (isSingleTurnMode) {
            // 如果有当前会话，先保存
            if (currentSession != null) {
                saveCurrentSession();
            }

            // 创建新会话
            currentSession = new Session(textContent);
            sessionHistory.add(0, currentSession);
            dbHelper.saveSession(currentSession);

            // 清空消息历史，准备新的对话
            messageHistory.clear();

            // 通知 WebView 清空界面
            mainHandler.post(() -> {
                webView.evaluateJavascript("javascript:clearMessages();", null);
            });
        }

//        System.out.println("Sending message: " + content);
//        System.out.println("Current model (synchronized): " + model);

        // 如果有文件附件，将文件信息编码到消息内容中供 WebView 显示
        String userDisplayContent = textContent;
        try {
            JsonObject json = new JsonParser().parse(content).getAsJsonObject();
            if (json.has("text") && json.has("files")) {
                JsonArray files = json.getAsJsonArray("files");
                JsonObject fileMeta = new JsonObject();
                fileMeta.addProperty("_hasFiles", true);
                fileMeta.add("_files", files);
                fileMeta.addProperty("_text", textContent);
                userDisplayContent = fileMeta.toString();
            }
        } catch (Exception ignored) {}

        Message userMsg = new Message("user", userDisplayContent);


        // 如果是新会话，创建会话
        if (currentSession == null) {
            currentSession = new Session(textContent);
            sessionHistory.add(0, currentSession);
        }

        // 添加消息到当前会话
        currentSession.addMessage(userMsg);
        if (prompt != null &&!prompt.isEmpty()) {
            Message systemPrompt = new Message("system", prompt);
            messageHistory.add(systemPrompt);
        }
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
        // 先清理之前的连接状态
        cleanupCurrentConnection();

                isReceiving = true;  // 设置标志
                // 转换消息格式
                List<ChatMessage> chatMessages = new ArrayList<>();

                // 检查是否包含文件附件（在 lambda 内重新解析）
                String rawContent = content;
                boolean needsMultimodal = false;
                String userText = "";
                JsonArray files = null;
                try {
                    JsonObject json = new JsonParser().parse(rawContent).getAsJsonObject();
                    if (json.has("text") && json.has("files")) {
                        needsMultimodal = true;
                        userText = json.get("text").getAsString();
                        files = json.getAsJsonArray("files");
                    }
                } catch (Exception ignored) {}

                if (needsMultimodal) {
                    // 构建多模态请求
                    String rawJson = buildMultimodalRequest(model, prompt, userText, files, messageHistory);
                    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                    RequestBody body = RequestBody.create(JSON, rawJson);
                    currentSubscription = openAiApi.streamChatCompletionRaw(body)
                            .subscribe(
                                    responseBody -> {
                                        currentResponseBody = responseBody;
                                        processStreamingResponse(responseBody);
                                    },
                                    error -> {
                                        boolean isRealInterrupt = interruptRequested &&
                                            (error instanceof java.io.InterruptedIOException ||
                                             error.getMessage() != null &&
                                             (error.getMessage().contains("Canceled") ||
                                              error.getMessage().contains("canceled") ||
                                              error.getMessage().contains("disposed") ||
                                              error.getMessage().contains("Socket")));
                                        if (isRealInterrupt || interruptRequested) {
                                            currentSubscription = null;
                                            isReceiving = false;
                                            interruptRequested = false;
                                            return;
                                        }
                                        mainHandler.post(() -> {
                                            handleNetworkError(error);
                                        });
                                    },
                                    () -> {
                                        currentSubscription = null;
                                        currentResponseBody = null;
                                        isReceiving = false;
                                        interruptRequested = false;
                                        mainHandler.post(() -> {
                                            webView.evaluateJavascript("javascript:onResponseComplete();", null);
                                        });
                                    }
                            );
                    return;
                }
                for (Message msg : messageHistory) {
                    chatMessages.add(new ChatMessage(msg.getRole(), msg.getContent()));
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
//                System.out.println("Sending request with model: " + getCurrentModel());
//                System.out.println("Full request: " + gson.toJson(request));

                currentSubscription = openAiApi.streamChatCompletion(request)
                        .subscribe(
                                responseBody -> {
                                    currentResponseBody = responseBody;
                                    processStreamingResponse(responseBody);
                                },
                                error -> {
                                    // 检查是否真的被中断了
                                    boolean isRealInterrupt = interruptRequested &&
                                        (error instanceof java.io.InterruptedIOException ||
                                         error.getMessage() != null &&
                                         (error.getMessage().contains("Canceled") ||
                                          error.getMessage().contains("canceled") ||
                                          error.getMessage().contains("disposed") ||
                                          error.getMessage().contains("Socket")));

                                    // 如果是中断请求导致的错误，不显示错误信息
                                    if (isRealInterrupt || interruptRequested) {
                                        // 已经中断，重置标志
                                        currentSubscription = null;
                                        isReceiving = false;
                                        interruptRequested = false;
                                        return;
                                    }
                                    // 网络错误时自动停止生成
                                    mainHandler.post(() -> {
                                        handleNetworkError(error);
                                    });
                                    currentSubscription = null;
                                    isReceiving = false;
                                    interruptRequested = false;
                                },
                                () -> {
                                    // 流完成时的处理
                                    currentSubscription = null;
                                    currentResponseBody = null;
                                    isReceiving = false;
                                    interruptRequested = false;
                                    mainHandler.post(() -> {
                                        webView.evaluateJavascript("javascript:onResponseComplete();", null);
                                    });
                                }
                        );

                // 启动中断监控线程（每 100ms 检查一次中断标志）
                Thread interruptMonitor = new Thread(() -> {
                    while (isReceiving && !interruptRequested) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    // 检测到中断请求，立即取消网络连接
                    if (interruptRequested && currentSubscription != null) {
                        currentSubscription.dispose();
                        currentSubscription = null;
                    }
                    if (interruptRequested && currentResponseBody != null) {
                        try {
                            currentResponseBody.close();
                        } catch (Exception e) {
                            // 忽略关闭错误
                        }
                        currentResponseBody = null;
                    }
                });
                interruptMonitor.start();

            } catch (Exception e) {
                // 如果是中断请求导致的错误，不显示错误信息
                if (interruptRequested) {
                    currentSubscription = null;
                    isReceiving = false;
                    interruptRequested = false;
                    return;
                }
//                System.out.println("Error sending message: " + e.getMessage());
//                e.printStackTrace();
                mainHandler.post(() -> {
                    if (!messageHistory.isEmpty()) {
                        Message lastMessage = messageHistory.get(messageHistory.size() - 1);
                        lastMessage.setContent("发生错误: " + e.getMessage());
                        updateAssistantMessage(lastMessage);
                    }
                });
                currentSubscription = null;
                isReceiving = false;
                interruptRequested = false;
            }
        });
    }

    @JavascriptInterface
    public String getMessageHistory() {
        return gson.toJson(messageHistory);
    }

    // 从 data URL 中提取纯 base64 字符串 (去掉 "data:image/...;base64," 前缀)
    private String extractBase64(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        return comma > 0 ? dataUrl.substring(comma + 1) : dataUrl;
    }

    // 从 data URL 中提取 MIME 类型 (从 "data:image/png;base64,..." 中提取 "image/png")
    private String extractMimeFromDataUrl(String dataUrl) {
        if (dataUrl.startsWith("data:")) {
            int semi = dataUrl.indexOf(';');
            return semi > 0 ? dataUrl.substring(5, semi) : "application/octet-stream";
        }
        return "application/octet-stream";
    }

    // 判断是否为文本类型的 MIME
    private boolean isTextMime(String mime) {
        return mime != null && (mime.startsWith("text/") ||
                mime.equals("application/json") ||
                mime.equals("application/xml") ||
                mime.equals("application/javascript") ||
                mime.equals("application/x-javascript"));
    }

    // 根据文件扩展名判断是否为文本文件
    private boolean isTextFileName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown") ||
                lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".yaml") ||
                lower.endsWith(".yml") || lower.endsWith(".csv") || lower.endsWith(".tsv") ||
                lower.endsWith(".log") || lower.endsWith(".cfg") || lower.endsWith(".conf") ||
                lower.endsWith(".ini") || lower.endsWith(".toml") || lower.endsWith(".env") ||
                lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".jsx") ||
                lower.endsWith(".tsx") || lower.endsWith(".py") || lower.endsWith(".java") ||
                lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".h") ||
                lower.endsWith(".hpp") || lower.endsWith(".cs") || lower.endsWith(".go") ||
                lower.endsWith(".rs") || lower.endsWith(".rb") || lower.endsWith(".php") ||
                lower.endsWith(".swift") || lower.endsWith(".kt") || lower.endsWith(".kts") ||
                lower.endsWith(".scala") || lower.endsWith(".sh") || lower.endsWith(".bash") ||
                lower.endsWith(".zsh") || lower.endsWith(".ps1") || lower.endsWith(".bat") ||
                lower.endsWith(".cmd") || lower.endsWith(".sql") || lower.endsWith(".r") ||
                lower.endsWith(".lua") || lower.endsWith(".pl") || lower.endsWith(".pm") ||
                lower.endsWith(".dart") || lower.endsWith(".groovy") || lower.endsWith(".gradle") ||
                lower.endsWith(".css") || lower.endsWith(".scss") || lower.endsWith(".less") ||
                lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml") ||
                lower.endsWith(".properties") || lower.endsWith(".gradle");
    }

    // 构建多模态（文本+图片）请求的 JSON
    private String buildMultimodalRequest(String model, String promptText, String userText, JsonArray files, List<Message> history) {
        String provider = config.getProvider();
        if (provider == null || provider.isEmpty()) {
            provider = "openai";
        }

        // custom / deepseek: 使用 OpenAI image_url 格式
        if ("custom".equals(provider) || "deepseek".equals(provider)) {
            return buildMultimodalRequestInternal(model, promptText, userText, files, history, "openai");
        }

        return buildMultimodalRequestInternal(model, promptText, userText, files, history, provider);
    }

    private String buildMultimodalRequestInternal(String model, String promptText, String userText, JsonArray files, List<Message> history, String provider) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("stream", true);
        root.addProperty("temperature", (double) config.getTemperature());

        JsonArray messages = new JsonArray();

        // system prompt
        if (promptText != null && !promptText.isEmpty()) {
            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            if ("gemini".equals(provider)) {
                // Gemini 暂不支持数组格式的 system message
                JsonArray sysContent = new JsonArray();
                JsonObject sysText = new JsonObject();
                sysText.addProperty("type", "text");
                sysText.addProperty("text", promptText);
                sysContent.add(sysText);
                sysMsg.add("content", sysContent);
            } else {
                sysMsg.addProperty("content", promptText);
            }
            messages.add(sysMsg);
        }

        // 之前的历史消息（纯文本）
        for (Message msg : history) {
            if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                String histContent = msg.getContent();
                if (histContent.contains("_hasFiles")) {
                    continue;
                }
                if (histContent.equals(userText) || (files != null && histContent.isEmpty())) {
                    continue;
                }
                JsonObject histMsg = new JsonObject();
                histMsg.addProperty("role", "user");
                if ("gemini".equals(provider)) {
                    JsonArray contentArr = new JsonArray();
                    JsonObject textObj = new JsonObject();
                    textObj.addProperty("type", "text");
                    textObj.addProperty("text", histContent);
                    contentArr.add(textObj);
                    histMsg.add("content", contentArr);
                } else {
                    histMsg.addProperty("content", histContent);
                }
                messages.add(histMsg);
            } else if ("assistant".equals(msg.getRole())) {
                JsonObject asstMsg = new JsonObject();
                asstMsg.addProperty("role", "assistant");
                if ("gemini".equals(provider)) {
                    JsonArray contentArr = new JsonArray();
                    JsonObject textObj = new JsonObject();
                    textObj.addProperty("type", "text");
                    textObj.addProperty("text", msg.getContent() != null ? msg.getContent() : "");
                    contentArr.add(textObj);
                    asstMsg.add("content", contentArr);
                } else {
                    asstMsg.addProperty("content", msg.getContent() != null ? msg.getContent() : "");
                }
                messages.add(asstMsg);
            }
        }

        // 当前用户消息（多模态）
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");

        if (files != null && files.size() > 0) {
            if ("gemini".equals(provider)) {
                // Gemini: content 为数组，所有文件统一用 inline_data (支持任意 MIME 类型)
                JsonArray contentArray = new JsonArray();
                if (userText != null && !userText.isEmpty()) {
                    JsonObject textPart = new JsonObject();
                    textPart.addProperty("type", "text");
                    textPart.addProperty("text", userText);
                    contentArray.add(textPart);
                }
                for (int i = 0; i < files.size(); i++) {
                    JsonObject fileObj = files.get(i).getAsJsonObject();
                    String data = fileObj.get("data").getAsString();
                    String mime = extractMimeFromDataUrl(data);
                    JsonObject inlinePart = new JsonObject();
                    inlinePart.addProperty("type", "inline_data");
                    JsonObject inlineData = new JsonObject();
                    inlineData.addProperty("mime_type", mime);
                    inlineData.addProperty("data", extractBase64(data));
                    inlinePart.add("inline_data", inlineData);
                    contentArray.add(inlinePart);
                }
                userMsg.add("content", contentArray);
            } else if ("claude".equals(provider)) {
                // Claude: content 为数组，图片用 image 类型，PDF 用 document 类型
                JsonArray contentArray = new JsonArray();
                if (userText != null && !userText.isEmpty()) {
                    JsonObject textPart = new JsonObject();
                    textPart.addProperty("type", "text");
                    textPart.addProperty("text", userText);
                    contentArray.add(textPart);
                }
                for (int i = 0; i < files.size(); i++) {
                    JsonObject fileObj = files.get(i).getAsJsonObject();
                    String data = fileObj.get("data").getAsString();
                    String mime = extractMimeFromDataUrl(data);
                    if (mime.startsWith("image/")) {
                        JsonObject imagePart = new JsonObject();
                        imagePart.addProperty("type", "image");
                        JsonObject source = new JsonObject();
                        source.addProperty("type", "base64");
                        source.addProperty("media_type", mime);
                        source.addProperty("data", extractBase64(data));
                        imagePart.add("source", source);
                        contentArray.add(imagePart);
                    } else {
                        JsonObject docPart = new JsonObject();
                        docPart.addProperty("type", "document");
                        JsonObject source = new JsonObject();
                        source.addProperty("type", "base64");
                        source.addProperty("media_type", mime);
                        source.addProperty("data", extractBase64(data));
                        docPart.add("source", source);
                        contentArray.add(docPart);
                    }
                }
                userMsg.add("content", contentArray);
            } else {
                // OpenAI / Azure / custom: content 为数组，图片用 image_url，文本类文件解码为代码块
                JsonArray contentArray = new JsonArray();
                if (userText != null && !userText.isEmpty()) {
                    JsonObject textPart = new JsonObject();
                    textPart.addProperty("type", "text");
                    textPart.addProperty("text", userText);
                    contentArray.add(textPart);
                }
                for (int i = 0; i < files.size(); i++) {
                    JsonObject fileObj = files.get(i).getAsJsonObject();
                    String data = fileObj.get("data").getAsString();
                    String mime = extractMimeFromDataUrl(data);
                    String fileName = fileObj.get("name").getAsString();
                    if (mime.startsWith("image/")) {
                        JsonObject imagePart = new JsonObject();
                        imagePart.addProperty("type", "image_url");
                        JsonObject imageUrl = new JsonObject();
                        imageUrl.addProperty("url", data);
                        imagePart.add("image_url", imageUrl);
                        contentArray.add(imagePart);
                    } else if (isTextMime(mime) || isTextFileName(fileName)) {
                        // 文本类文件：解码 base64 为文本，以代码块形式嵌入
                        try {
                            byte[] decoded = android.util.Base64.decode(extractBase64(data), android.util.Base64.DEFAULT);
                            String fileContent = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";
                            JsonObject textPart = new JsonObject();
                            textPart.addProperty("type", "text");
                            textPart.addProperty("text", "\n\n```" + ext + "\n// " + fileName + "\n" + fileContent + "\n```\n");
                            contentArray.add(textPart);
                        } catch (Exception e) {
                            JsonObject textPart = new JsonObject();
                            textPart.addProperty("type", "text");
                            textPart.addProperty("text", "\n[附件: " + fileName + "]\n");
                            contentArray.add(textPart);
                        }
                    } else {
                        // 二进制文件（PDF 等）：只能以文字标记
                        JsonObject textPart = new JsonObject();
                        textPart.addProperty("type", "text");
                        textPart.addProperty("text", "\n[附件: " + fileName + "]\n");
                        contentArray.add(textPart);
                    }
                }
                userMsg.add("content", contentArray);
            }
        } else {
            // 无文件：纯文本
            if ("gemini".equals(provider)) {
                JsonArray contentArray = new JsonArray();
                JsonObject textPart = new JsonObject();
                textPart.addProperty("type", "text");
                textPart.addProperty("text", userText != null ? userText : "");
                contentArray.add(textPart);
                userMsg.add("content", contentArray);
            } else {
                userMsg.addProperty("content", userText != null ? userText : "");
            }
        }

        messages.add(userMsg);
        root.add("messages", messages);
        return root.toString();
    }

    private void notifyWebViewNewMessage(Message message) {
        mainHandler.post(() -> {
//            System.out.println("发送新消息到 WebView: ID=" + message.getId() + ", Role=" + message.getRole());
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
//        System.out.println("Updating message: " + message.getId());
//        System.out.println("Content: " + message.getContent());
//        System.out.println("Formatted content: " + formattedContent);
        
        String updateScript = String.format(
            "javascript:updateMessage(%s);",
            gson.toJson(message)
        );
        webView.evaluateJavascript(updateScript, null);
    }

    // 修改流式处理的执行部分，回到 BufferedReader
    private void processStreamingResponse(ResponseBody responseBody) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(responseBody.byteStream())
            );
            currentReader = reader;

            String line;
            while ((line = reader.readLine()) != null) {
                // 每次读取一行后立即检查中断标志
                if (interruptRequested) {
                    handleInterrupt();
                    return;
                }
//                System.out.println("Raw line: " + line);
                handleStreamResponse(line);

//                // 每次处理后再次检查中断标志，确保快速响应中断请求
//                if (interruptRequested) {
//                    handleInterrupt();
//                    return;
//                }
            }

//            if (interruptRequested) {
//                // 被中断的情况已经由 interruptResponse 处理
//            }
        } catch (Exception e) {
            if (!interruptRequested) {
                // 只有非中断引起的异常才显示错误
//                System.out.println("Error processing response: " + e.getMessage());
//                e.printStackTrace();
                mainHandler.post(() -> {
                    if (!messageHistory.isEmpty()) {
                        Message lastMessage = messageHistory.get(messageHistory.size() - 1);
                        lastMessage.setContent("发生错误: " + e.getMessage());
                        updateAssistantMessage(lastMessage);
                    }
                    webView.evaluateJavascript("javascript:onResponseComplete();", null);
                });
            }
        } finally {
            // 清理资源
            try {
                if (currentReader != null) {
                    currentReader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            currentReader = null;
            currentSubscription = null;
            // 注意：这里不重置 isReceiving，因为可能在 interruptResponse 中已经重置
            // 但如果正常完成，需要重置
            if (!interruptRequested) {
                isReceiving = false;
            }
            interruptRequested = false;  // 重置中断标志
        }
    }

    // 处理中断的辅助方法
    private void handleInterrupt() {
        mainHandler.post(() -> {
            if (!messageHistory.isEmpty()) {
                Message lastMessage = messageHistory.get(messageHistory.size() - 1);
                if ("assistant".equals(lastMessage.getRole())) {
                    String currentContent = lastMessage.getContent();
                    // 检查是否已经包含"[已停止生成]"标记，避免重复添加
                    if (currentContent != null && currentContent.contains("[已停止生成]")) {
                        // 已经添加过，跳过
                    } else {
                        // 如果已经有内容，添加中断标记
                        if (currentContent != null && !currentContent.isEmpty()) {
                            lastMessage.setContent(currentContent + "\n\n[已停止生成]");
                        } else {
                            // 如果还没有任何内容，直接设置中断消息
                            lastMessage.setContent("[已停止生成]");
                        }
                        // 直接更新 WebView
                        String script = String.format(
                            "javascript:updateMessageContent('%s', %s);",
                            lastMessage.getId(),
                            gson.toJson(formatContent(lastMessage.getContent()))
                        );
                        webView.evaluateJavascript(script, null);
                        saveCurrentSession();
                    }
                }
            }
            // 通知 JavaScript 响应完成
            webView.evaluateJavascript("javascript:onResponseComplete();", null);
            Toast.makeText(context, "已停止生成回复1", Toast.LENGTH_SHORT).show();
        });
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
//                System.out.println("未找到消息: " + messageId);
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
                    deckId = getOrCreateDeck(AI_CHAT);
                    if (deckId == -1) {
                        return false;
                    }
                }
                
                // Continue with existing code...
//                System.out.println("尝试保存消息ID: " + messageId);
//                System.out.println("当前消息历史大小: " + messageHistory.size());
                
                // Print all message IDs and roles for debugging
//                System.out.println("消息历史列表:");
//                for (Message msg : messageHistory) {
//                    System.out.println("ID: " + msg.getId() + ", Role: " + msg.getRole() + ", Content长度: " +
//                        (msg.getContent() != null ? msg.getContent().length() : 0));
//                }

                String question = "";

                // Find the most recent user message before the target message
                int targetIndex = messageHistory.indexOf(targetMessage);
                for (int i = targetIndex - 1; i >= 0; i--) {
                    Message msg = messageHistory.get(i);
//                    System.out.println("向前查找用户消息: " + msg.getId() + ", 角色: " + msg.getRole());
                    if ("user".equals(msg.getRole())) {
                        question = msg.getContent();
                        break;
                    }
                }

//                System.out.println("找到问题: " + question);
//                System.out.println("找到答案: " + answer);

                if (question.isEmpty() || answer.isEmpty()) {
//                    Toast.makeText(context, "未找到完整的问答对", Toast.LENGTH_SHORT).show();
//                    System.out.println("未找到完整的问答对: question=" + question + ", answer=" + answer);
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
                    if (AI_CHAT.equals(model.getValue())) {
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
                            AI_CHAT,
                            fieldNames,
                            cardNames,
                            questionFormats,
                            answerFormats,
                            css,
                            deckId,
                            null
                        );
                    } catch (Exception e) {
//                        System.out.println("创建模型失败: " + e.getMessage());
//                        e.printStackTrace();
                        Toast.makeText(context, "创建模型失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }

                // 在保存笔记之前，处理 Markdown 和 LaTeX，并提取图片到 collection.media
                System.out.println("=== saveToAnki: question contains _hasFiles: " + (question != null && question.contains("_hasFiles")));
                System.out.println("=== saveToAnki: answer contains _hasFiles: " + (answer != null && answer.contains("_hasFiles")));
                String processedQuestion = processHasFilesContent(question);
                String processedAnswer = processHasFilesContent(answer);
                System.out.println("=== saveToAnki: question length before=" + question.length() + " after=" + processedQuestion.length());
                System.out.println("=== saveToAnki: processedQuestion startsWith _hasFiles: " + processedQuestion.contains("_hasFiles"));

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
//                e.printStackTrace();
//                System.out.println("保存失败: " + e.getMessage());
//                System.out.println("异常堆栈: ");
//                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(context, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                return false;
            }
        } catch (Exception e) {
//            e.printStackTrace();
//            System.out.println("保存失败: " + e.getMessage());
//            System.out.println("异常堆栈: ");
//            e.printStackTrace();
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
//            e.printStackTrace();
//            System.out.println("JSON 处理错误: " + e.getMessage());
//            System.out.println("原始内容: " + content);
            
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
                // 如果消息列表为空，直接删除这个空会话
                if (messageHistory.isEmpty()) {
                    String emptySessionId = currentSession.getId();
                    currentSession = null;
                    if (emptySessionId != null) {
                        dbHelper.deleteSession(emptySessionId);
                    }
                    return;
                }
                // 更新会话内容
                currentSession.getMessages().clear();
                currentSession.getMessages().addAll(messageHistory);
                // 保存到数据库
                dbHelper.saveSession(currentSession);
//                System.out.println("会话已保存: " + currentSession.getId() + ", 消息数: " + currentSession.getMessages().size());
            } catch (Exception e) {
//                e.printStackTrace();
//                System.out.println("保存会话失败: " + e.getMessage());
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
//            e.printStackTrace();
//            System.out.println("删除失败: " + e.getMessage());
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

    /**
     * 删除指定消息
     * @param messageId 要删除的消息ID
     * @return 是否删除成功
     */
    @JavascriptInterface
    public boolean deleteMessage(String messageId) {
        try {
            // 查找消息
            Message targetMessage = null;
            int targetIndex = -1;

            for (int i = 0; i < messageHistory.size(); i++) {
                if (messageId.equals(messageHistory.get(i).getId())) {
                    targetMessage = messageHistory.get(i);
                    targetIndex = i;
                    break;
                }
            }

            if (targetMessage == null) {
                mainHandler.post(() -> {
                    Toast.makeText(context, "未找到消息", Toast.LENGTH_SHORT).show();
                });
                return false;
            }

            // 如果删除的是助手消息，需要同时删除对应的用户消息（如果有的话）
            // 注意：先记录要删除的用户消息ID，然后再删除助手消息，最后删除用户消息
            String userMessageIdToDelete = null;
            if ("assistant".equals(targetMessage.getRole())) {
                // 找到前一个用户消息（如果有的话）
                for (int i = targetIndex - 1; i >= 0; i--) {
                    Message msg = messageHistory.get(i);
                    if ("user".equals(msg.getRole())) {
                        userMessageIdToDelete = msg.getId();
                        break;
                    }
                }
            }

            // 先删除助手消息（目标消息）
            messageHistory.remove(targetIndex);

            // 如果需要删除用户消息，现在索引已经变化，需要重新查找
            if (userMessageIdToDelete != null) {
                for (int i = 0; i < messageHistory.size(); i++) {
                    if (userMessageIdToDelete.equals(messageHistory.get(i).getId())) {
                        final String userMsgId = messageHistory.get(i).getId();
                        messageHistory.remove(i);
                        // 通知前端删除用户消息
                        mainHandler.post(() -> {
                            webView.evaluateJavascript("javascript:removeMessageFromUI('" + userMsgId + "');", null);
                        });
                        break;
                    }
                }
            }

            // 通知前端删除消息
            mainHandler.post(() -> {
                webView.evaluateJavascript("javascript:removeMessageFromUI('" + messageId + "');", null);
                Toast.makeText(context, "消息已删除", Toast.LENGTH_SHORT).show();
            });

            // 保存当前会话
            saveCurrentSession();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            mainHandler.post(() -> {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
            });
            return false;
        }
    }

    public synchronized void updateConfig(String apiKey, String baseUrl) {
//        System.out.println("Updating ChatService config");
        this.openAiApi = createOpenAiApi(apiKey, baseUrl);
    }

    // 添加中断当前响应的方法
    private void interruptCurrentResponse() {
        interruptRequested = true;  // 设置中断标志

        // 1. 先取消订阅（关键！这一步会通知 Retrofit 中断连接）
        if (currentSubscription != null && !currentSubscription.isDisposed()) {
            currentSubscription.dispose();
            currentSubscription = null;
        }

        // 2. 关闭 ResponseBody（会中断底层连接）
        if (currentResponseBody != null) {
            try {
                currentResponseBody.close();
            } catch (Exception e) {
                System.out.println("Error closing ResponseBody: " + e.getMessage());
            }
            currentResponseBody = null;
        }

        // 3. 关闭 reader（如果存在）
        if (currentReader != null) {
            try {
                currentReader.close();
            } catch (IOException e) {
                System.out.println("Error closing reader: " + e.getMessage());
            }
            currentReader = null;
        }

        // 4. 重置接收标志
        isReceiving = false;

//        System.out.println("Response interrupted and cleaned up");
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
                deckId = getOrCreateDeck(AI_CHAT);
                if (deckId == -1) {
                    return false;
                }
            }
            
            // 获取模型列表
            Map<Long, String> models = null;
            try {
                models = ankiApi.getModelList();
            } catch (Exception e) {
//                System.out.println("获取模型列表失败: " + e.getMessage());
//                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(context, "获取模型列表失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                return false;
            }
            
            // 查找 AI Chat 模型
            long oldModelId = -1;
            for (Map.Entry<Long, String> model : models.entrySet()) {
                if (AI_CHAT.equals(model.getValue())) {
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
                    
//                    // 现在创建最终的模型
//                    long finalModelId = ankiApi.addNewCustomModel(
//                        AI_CHAT,
//                        fieldNames,
//                        cardNames,
//                        questionFormats,
//                        answerFormats,
//                        css,
//                        deckId,
//                        null
//                    );

                    long finalModelId = updateModel(AI_CHAT, fieldNames, cardNames, questionFormats, answerFormats, css);
                    
//                    System.out.println("创建或更新模型成功，ID: " + finalModelId);

                    // 通知用户模板已更新
                    mainHandler.post(() -> {
                        Toast.makeText(context, "Anki模板已更新，新卡片将使用新模板\nID: " + finalModelId, Toast.LENGTH_LONG).show();
                    });
                    
                    return true;
                } catch (Exception e) {
//                    System.out.println("更新模型失败: " + e.getMessage());
//                    e.printStackTrace();
                    mainHandler.post(() -> {
                        Toast.makeText(context, "更新模型失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                    return false;
                }
            } else {
                // 如果没有找到旧模型，直接创建新模型
                try {
                    long modelId = ankiApi.addNewCustomModel(
                        AI_CHAT,
                        fieldNames,
                        cardNames,
                        questionFormats,
                        answerFormats,
                        css,
                        deckId,
                        null
                    );
//                    System.out.println("创建新模型成功，ID: " + modelId);
                    return true;
                } catch (Exception e) {
//                    System.out.println("创建模型失败: " + e.getMessage());
//                    e.printStackTrace();
                    mainHandler.post(() -> {
                        Toast.makeText(context, "创建模型失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                    return false;
                }
            }
        } catch (Exception e) {
//            System.out.println("更新模板失败: " + e.getMessage());
//            e.printStackTrace();
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
//            System.out.println("成功获取牌组列表: " + (decks != null ? decks.size() : "null"));
        } catch (Exception e) {
//            System.out.println("获取牌组列表失败: " + e.getMessage());
//            e.printStackTrace();
            mainHandler.post(() -> {
                Toast.makeText(context, "获取牌组列表失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
            return null;
        }
        
        if (decks == null) {
//            System.out.println("牌组列表为空，可能是权限问题");
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
//            System.out.println("创建新牌组 " + deckName + "，ID: " + deckId);
            return deckId;
        } catch (Exception e) {
//            System.out.println("创建牌组失败: " + e.getMessage());
//            e.printStackTrace();
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

            // 立即更新 UI 显示中断消息（使用 post 确保在主线程执行）
            mainHandler.post(() -> {
                if (!messageHistory.isEmpty()) {
                    Message lastMessage = messageHistory.get(messageHistory.size() - 1);
                    if ("assistant".equals(lastMessage.getRole())) {
                        String currentContent = lastMessage.getContent();
                        // 检查是否已经包含"[已停止生成]"标记，避免重复添加
                        if (currentContent != null && currentContent.contains("[已停止生成]")) {
                            // 已经添加过，跳过
                        } else {
                            // 如果已经有内容，添加中断标记
                            if (currentContent != null && !currentContent.isEmpty()) {
                                lastMessage.setContent(currentContent + "\n\n[已停止生成]");
                            } else {
                                // 如果还没有任何内容，直接设置中断消息
                                lastMessage.setContent("[已停止生成]");
                            }
                            // 直接更新 WebView，不等待后台线程
                            String script = String.format(
                                "javascript:updateMessageContent('%s', %s);",
                                lastMessage.getId(),
                                gson.toJson(formatContent(lastMessage.getContent()))
                            );
                            webView.evaluateJavascript(script, null);
                            // 保存到数据库
                            saveCurrentSession();
                        }
                    }
                }
                // 通知 JavaScript 响应完成
                webView.evaluateJavascript("javascript:onResponseComplete();", null);
                Toast.makeText(context, "已停止生成回复2", Toast.LENGTH_SHORT).show();
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
                currentSubscription = null;
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

    public long updateModel(String name, String[] fields, String[] cards, String[] qfmt, String[] afmt, String css) {
        // Get modelId
        Long modelId = mAnkidroid.findModelIdByName(name, fields.length);
        Uri modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(modelId));
        if (modelUri != null) {
            ContentValues values = new ContentValues();
            values.put(FlashCardsContract.Model.CSS, css);
            mResolver.update(modelUri, values, null, null);
            // Set the remaining template parameters
            Uri templatesUri = Uri.withAppendedPath(modelUri, "templates");
            for (int i = 0; i < cards.length; i++) {
                Uri uri = Uri.withAppendedPath(templatesUri, Integer.toString(i));
                values = new ContentValues();
                values.put(FlashCardsContract.CardTemplate.NAME, cards[i]);
                values.put(FlashCardsContract.CardTemplate.QUESTION_FORMAT, qfmt[i]);
                values.put(FlashCardsContract.CardTemplate.ANSWER_FORMAT, afmt[i]);
                try {
                    mResolver.update(uri, values, null, null);
                    //                mResolver.update(uri, values, null, null);
                }catch (Exception e) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mResolver.insert(uri, values, null);
                    } else {
                        ToastUtil.show(context, String.format("%s: Model is malformed.", name, cards[i]));
                        return -1;
                    }
                }
            }
        }
        return modelId;
    }

    // ========== AnkiDroid collection.media 图片存储 ==========

    private String generateImageFilename(String originalName) {
        return originalName;
    }

    /**
     * 将图片保存到 AnkiDroid/collection.media/ 目录
     * 1) 优先通过 AnkiDroid API addMediaFromUri（使用 FileProvider content:// URI 保证跨应用可读）
     * 2) 降级：直接 File I/O（targetSdk<30 的设备可以直接写）
     * @return 实际保存的文件名，失败返回 null
     */
    private String saveImageToCollectionMedia(byte[] imageData, String fileName) {
        // 方案1: 通过 AnkiDroid API addMediaFromUri（使用 FileProvider 生成 content:// URI）
        try {
            File cacheFile = new File(context.getCacheDir(), fileName);
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                fos.write(imageData);
            }
            Uri contentUri = androidx.core.content.FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", cacheFile);
            // 授予 AnkiDroid 临时读取权限
            context.grantUriPermission("com.ichi2.anki", contentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String nameNoExt = fileName.contains(".") ?
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            String apiResult = ankiApi.addMediaFromUri(contentUri, nameNoExt, "image");
            // 回收权限
            context.revokeUriPermission("com.ichi2.anki", contentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cacheFile.delete();
            if (apiResult != null) {
                String savedName = fileName;
                int s = apiResult.indexOf("src=\"");
                int e2 = apiResult.lastIndexOf("\"");
                if (s >= 0 && e2 > s + 5) {
                    savedName = apiResult.substring(s + 5, e2);
                }
                System.out.println("=== saveImageToCollectionMedia: addMediaFromUri OK: " + savedName);
                return savedName;
            }
            System.out.println("=== saveImageToCollectionMedia: addMediaFromUri returned null, trying direct I/O");
        } catch (Exception e) {
            System.out.println("=== saveImageToCollectionMedia: addMediaFromUri failed: " + e.getMessage());
        }

        // 方案2: 直接 File I/O 写入（适用于 targetSdk<30 或已有 MANAGE_EXTERNAL_STORAGE 权限）
        try {
            File mediaDir = new File(
                Environment.getExternalStorageDirectory(),
                "AnkiDroid/collection.media");
            if (!mediaDir.exists()) {
                mediaDir.mkdirs();
            }
            File outFile = new File(mediaDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(imageData);
                fos.flush();
            }
            System.out.println("=== saveImageToCollectionMedia: direct I/O OK: " + outFile.getAbsolutePath());
            return fileName;
        } catch (Exception e) {
            System.out.println("=== saveImageToCollectionMedia: direct I/O also failed: " + e.getMessage());
            return null;
        }
    }

    private String processHasFilesContent(String rawContent) {
        if (rawContent == null || !rawContent.contains("_hasFiles")) {
            return rawContent;
        }
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(rawContent).getAsJsonObject();
            if (!json.has("_files")) return rawContent;
            com.google.gson.JsonArray files = json.getAsJsonArray("_files");
            System.out.println("=== processHasFilesContent: found " + files.size() + " files");

            StringBuilder result = new StringBuilder();
            if (json.has("_text")) {
                result.append(json.get("_text").getAsString());
            }

            for (int i = 0; i < files.size(); i++) {
                com.google.gson.JsonObject f = files.get(i).getAsJsonObject();
                String name = f.get("name").getAsString();
                String data = f.get("data").getAsString();
                String type = f.has("type") ? f.get("type").getAsString() : "";
                result.append("\n");
                System.out.println("=== processHasFilesContent: file[" + i + "] name=" + name + " type=" + type + " data.len=" + data.length() + " startsWithData=" + data.startsWith("data:"));

                if (type.startsWith("image/") && data.startsWith("data:")) {
                    String mediaFileName = generateImageFilename(name);
                    System.out.println("=== processHasFilesContent: saving image to " + mediaFileName);
                    try {
                        String base64Data = data.substring(data.indexOf(',') + 1);
                        byte[] decoded = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                        String savedName = saveImageToCollectionMedia(decoded, mediaFileName);
                        if (savedName != null) {
                            result.append("<img src=\"").append(savedName).append("\" />\n\n");
                            System.out.println("=== processHasFilesContent: image ok, output <img> tag: " + savedName);
                        } else {
                            result.append("![").append(name).append("](").append(data).append(")\n\n");
                            System.out.println("=== processHasFilesContent: image save failed, fallback data URL");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        result.append("![").append(name).append("](").append(data).append(")\n\n");
                    }
                } else if (data.startsWith("data:")) {
                    // 非图片文件：解码 base64 为文本内容，用 <details><summary> 折叠
                    try {
                        String base64Data = data.substring(data.indexOf(',') + 1);
                        byte[] decoded = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                        String textContent = new String(decoded, "UTF-8");
                        result.append("<details>\n<summary>📄 ").append(name).append("</summary>\n\n");
                        result.append(textContent).append("\n\n</details>\n\n");
                        System.out.println("=== processHasFilesContent: non-image file decoded, length=" + textContent.length());
                    } catch (Exception e) {
                        System.out.println("=== processHasFilesContent: decode failed: " + e.getMessage());
                        result.append("![").append(name).append("](").append(data).append(")\n\n");
                    }
                } else {
                    result.append("![").append(name).append("](").append(data).append(")\n\n");
                }
            }
            return result.toString();
        } catch (Exception e) {
            System.out.println("processHasFilesContent error: " + e.getMessage());
            e.printStackTrace();
            return rawContent;
        }
    }

    /**
     * 清理当前连接状态
     */
    private void cleanupCurrentConnection() {
        // 取消订阅
        if (currentSubscription != null && !currentSubscription.isDisposed()) {
            currentSubscription.dispose();
            currentSubscription = null;
        }

        // 关闭 ResponseBody
        if (currentResponseBody != null) {
            try {
                currentResponseBody.close();
            } catch (Exception e) {
                System.out.println("Error closing ResponseBody in cleanup: " + e.getMessage());
            }
            currentResponseBody = null;
        }

        // 关闭 reader
        if (currentReader != null) {
            try {
                currentReader.close();
            } catch (Exception e) {
                System.out.println("Error closing reader in cleanup: " + e.getMessage());
            }
            currentReader = null;
        }

        // 重置标志
        isReceiving = false;
        interruptRequested = false;
    }

    /**
     * 处理网络错误，自动停止生成
     */
    private void handleNetworkError(Throwable error) {
        // 清理连接
        cleanupCurrentConnection();

        // 显示错误消息
        String errorMessage = error.getMessage();
        String displayMessage;

        // 判断错误类型
        if (errorMessage != null) {
            if (errorMessage.contains("connect timed out") || errorMessage.contains("ConnectException")) {
                displayMessage = "连接超时，请检查网络";
            } else if (errorMessage.contains("connection refused") || errorMessage.contains("ConnectionRefused")) {
                displayMessage = "连接被拒绝，请检查服务器";
            } else if (errorMessage.contains("UnknownHost") || errorMessage.contains("DNS")) {
                displayMessage = "无法解析服务器地址";
            } else if (errorMessage.contains("SSL") || errorMessage.contains("handshake")) {
                displayMessage = "SSL连接失败";
            } else if (errorMessage.contains("stream") || errorMessage.contains("Socket")) {
                displayMessage = "连接中断，请重试";
            } else {
                displayMessage = "网络错误: " + errorMessage;
            }
        } else {
            displayMessage = "网络连接失败";
        }

        // 查找助手消息ID
        String assistantMessageId = "";
        String userMessageContent = "";

        for (int i = messageHistory.size() - 1; i >= 0; i--) {
            Message msg = messageHistory.get(i);
            if ("assistant".equals(msg.getRole()) && msg.getContent() != null && msg.getContent().isEmpty()) {
                assistantMessageId = msg.getId();
                // 找前一个用户消息的内容
                if (i > 0) {
                    Message userMsg = messageHistory.get(i - 1);
                    if ("user".equals(userMsg.getRole())) {
                        userMessageContent = userMsg.getContent();
                    }
                }
                break;
            }
        }

        // 删除助手消息（会同时删除用户消息）
        if (!assistantMessageId.isEmpty()) {
            deleteMessage(assistantMessageId);
            // 恢复用户消息到输入框
            if (userMessageContent != null && !userMessageContent.isEmpty()) {
                final String restoreContent = userMessageContent;
                mainHandler.post(() -> {
                    webView.evaluateJavascript("javascript:(function() {" +
                        "var input = document.getElementById('message-input');" +
                        "if(input) { input.value = `" + restoreContent.replace("`", "\\`") + "`; input.disabled = false; }" +
                        "})();", null);
                });
            }
        }

        // 用 Toast 显示错误
        Toast.makeText(context, displayMessage, Toast.LENGTH_LONG).show();

        // 通知 JavaScript 响应完成，并恢复按钮状态
        mainHandler.post(() -> {
            webView.evaluateJavascript("javascript:(function() {" +
                "toggleSendInterruptButtons(false);" +
                "onResponseComplete();" +
                "})();", null);
        });
    }

    @JavascriptInterface
    public String loadPrompts() {
        List<Prompt> prompts = dbHelper.getAllPrompts();
        List<Map<String, String>> list = new ArrayList<>();
        for (Prompt p : prompts) {
            Map<String, String> map = new HashMap<>();
            map.put("title", p.getTitle());
            map.put("content", p.getContent());
            list.add(map);
        }
        return gson.toJson(list);
    }
}