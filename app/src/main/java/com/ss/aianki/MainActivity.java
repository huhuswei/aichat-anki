package com.ss.aianki;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.widget.AdapterView;
import android.app.AlertDialog;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ChatService chatService;
    private AIConfigManager configManager;
    private Spinner serverSpinner;
    private Spinner modelSpinner;
    private Spinner formatSpinner;
    private ChipGroup promptChipGroup;
    private DatabaseHelper dbHelper;
    private Spinner deckSpinner;
    private List<Map<String, Object>> deckList = new ArrayList<>();
    private static final String PREF_SELECTED_DECK_ID = "selected_deck_id";
    
    // 添加一个标志来跟踪初始化状态
    private boolean isInitializing = false;
    private DeckPreferences deckPreferences;
    private List<Map<String, Object>> allDeckList = new ArrayList<>(); // All decks from Anki
    private List<Map<String, Object>> filteredDeckList = new ArrayList<>(); // Filtered decks for display
    private String pendingSharedText = null;

    private LinearLayout spinnerContainer;
    private HorizontalScrollView promptHScrollView;
    private LinearLayout indicatorBar;
    private TextView currentSelectionText;
    private int isSpinnerContainerVisible = 1;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DarkModeUtils.initDarkMode(MainActivity.this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configManager = new AIConfigManager(this);
        
        // Initialize deck preferences
        deckPreferences = new DeckPreferences(this);
        
        // 初始化所有 UI 组件
        initializeUI();
        
        // 配置 WebView
        setupWebView();
        
        // 初始化 ChatService
        initializeChatService();
        
        // 加载 HTML 页面
        webView.loadUrl("file:///android_asset/chat.html");
        
        dbHelper = new DatabaseHelper(this);
        promptChipGroup = findViewById(R.id.promptChipGroup);
        
        // Load prompts
        loadPrompts();
        
        // Initialize deck spinner
        deckSpinner = findViewById(R.id.deckSpinner);
        deckSpinner.setOnLongClickListener(v-> {showDeckFilterDialog();return true;});

        checkAnkiPermission();
        // Load Anki decks
        loadAnkiDecks();

        // Handle initial intent
        handleIntent(getIntent());

        // Initialize views
        spinnerContainer = findViewById(R.id.spinnerContainer);
        promptHScrollView = findViewById(R.id.promptHScrollView);
        indicatorBar = findViewById(R.id.indicatorBar);
        currentSelectionText = findViewById(R.id.currentSelectionText);
        // Set up click listener
        indicatorBar.setOnClickListener(v -> toggleSpinnerContainer());

        // Update selection text when spinners change
        setupSpinnerListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 只更新服务器选择器，不重新初始化 ChatService
//        updateServerSpinner();
        initializeUI();
        loadPrompts(); // Reload prompts when returning to activity
        loadAnkiDecks();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();
        String sharedText = "";
        if (type != null) {
            if (Intent.ACTION_SEND.equals(action)) {
                if ("text/plain".equals(type)) {
                     sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);

                }
            } else if(Intent.ACTION_PROCESS_TEXT.equals(action)) {
                if ("text/plain".equals(type)) {
                    sharedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
                }
            }
            if (sharedText != null) {
                pendingSharedText = sharedText;
                processPendingSharedText();
            }
        }
    }

    private void processPendingSharedText() {
        if (pendingSharedText != null && webView != null) {
            webView.evaluateJavascript(
                "typeof handleSharedText === 'function'",
                value -> {
                    if ("true".equals(value)) {
                        handleSharedText(pendingSharedText);
                        pendingSharedText = null;
                    }
                }
            );
        }
    }

    private void initializeUI() {
        // 初始化服务器选择器
        serverSpinner = findViewById(R.id.serverSpinner);
        updateServerSpinner();
        
        // 初始化格式选择器
        formatSpinner = findViewById(R.id.formatSpinner);
        String[] formats = {"Markdown", "HTML"};
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, formats);
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(formatAdapter);
        formatSpinner.setSelection(0);
        
        // 初始化模型选择器
        modelSpinner = findViewById(R.id.modelSpinner);
        
        setupSpinnerListeners();
    }

    private void setupSpinnerListeners() {
        // 服务器选择事件
        serverSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AIServerConfig config = configManager.getAllConfigs().get(position);
                configManager.setCurrentConfig(config.getId());
                
                // 更新模型选择器
                updateModelSpinner(config);

                // 重新初始化 ChatService
                initializeChatService();
                
                // 通知 JavaScript 重新检查
                webView.evaluateJavascript("javascript:checkChatAndroid();", null);

                Log.d("MainActivity", "Server changed to: " + config.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 模型选择事件
        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedModel = parent.getItemAtPosition(position).toString();
                if (chatService != null) {
                    chatService.setModel(selectedModel);
                    
                    // 保存当前选择的模型到配置
                    AIServerConfig currentConfig = configManager.getCurrentConfig();
                    if (currentConfig != null) {
                        currentConfig.setLastSelectedModel(selectedModel);
                        configManager.saveConfig(currentConfig);
                        Log.d("MainActivity", "Saved selected model: " + selectedModel);
                    }

                    Log.d("MainActivity", "Model changed to: " + chatService.getCurrentModel());
                }

                updateSelectionText();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateSelectionText();
            }
        });

        // 格式选择事件
        formatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedFormat = parent.getItemAtPosition(position).toString();
                if (chatService != null) {
                    chatService.setOutputFormat(selectedFormat);
                    Log.d("MainActivity", "Format changed to: " + selectedFormat);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void initializeChatService() {
        if (isInitializing) {
            Log.d("MainActivity", "ChatService initialization already in progress");
            return;
        }
        
        isInitializing = true;
        try {
            AIServerConfig config = configManager.getCurrentConfig();
            if (config != null) {
                Log.d("MainActivity", "Initializing ChatService with config: " + config.getName());
                
                // 如果已存在 ChatService，则只更新配置而不是创建新实例
                if (chatService == null) {
                    chatService = new ChatService(webView, config.getApiKey(), config.getBaseUrl());
                    webView.addJavascriptInterface(chatService, "ChatAndroid");
                    Log.d("MainActivity", "Created new ChatService instance");
                } else {
                    // 更新现有实例的配置
                    chatService.updateConfig(config.getApiKey(), config.getBaseUrl());
                    Log.d("MainActivity", "Updated existing ChatService config");
                }
                
                // 设置当前选中的模型
                if (modelSpinner.getSelectedItem() != null) {
                    String selectedModel = modelSpinner.getSelectedItem().toString();
                    chatService.setModel(selectedModel);
                    Log.d("MainActivity", "Model set in ChatService: " + chatService.getCurrentModel());
                }
                
                // 设置当前选中的格式
                if (formatSpinner.getSelectedItem() != null) {
                    String selectedFormat = formatSpinner.getSelectedItem().toString();
                    chatService.setOutputFormat(selectedFormat);
                    Log.d("MainActivity", "Format set to: " + selectedFormat);
                }
                
                // 通知 JavaScript ChatAndroid 已更新
                webView.evaluateJavascript(
                    "javascript:console.log('ChatAndroid updated');" +
                    "if (typeof checkChatAndroid === 'function') {" +
                    "    checkChatAndroid();" +
                    "}", 
                    null
                );
            } else {
                Log.e("MainActivity", "No server config available");
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error initializing ChatService", e);
            e.printStackTrace();
        } finally {
            isInitializing = false;
        }
    }

    private void setupWebView() {
        webView = findViewById(R.id.webView);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        
        // Set WebView background to match app theme
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        webView.setBackgroundColor(typedValue.data);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                DarkModeUtils.isDarkMode(MyApplication.getContext())) {
            ws.setForceDark(WebSettings.FORCE_DARK_ON);
        }
        
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Inject the JavaScript function
                webView.evaluateJavascript(
                    "window.handleSharedText = function(text) {" +
                    "   const input = document.getElementById('message-input');" +
                    "   if (input) {" +
                    "       input.value = text;" +
                    "       input.dispatchEvent(new Event('input', { bubbles: true }));" +
                    "   }" +
                    "};", 
                    null
                );
                
                // Process any pending shared text
                processPendingSharedText();
            }
        });
    }

    private void updateServerSpinner() {
        List<AIServerConfig> configs = configManager.getAllConfigs();
        String[] items = configs.stream()
            .map(AIServerConfig::getName)
            .toArray(String[]::new);
        
        MarqueeSpinnerAdapter adapter = new MarqueeSpinnerAdapter(this,
            android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        serverSpinner.setAdapter(adapter);
        
        // 选中当前配置
        AIServerConfig current = configManager.getCurrentConfig();
        if (current != null) {
            for (int i = 0; i < configs.size(); i++) {
                if (configs.get(i).getId().equals(current.getId())) {
                    serverSpinner.setSelection(i);
                    break;
                }
            }
        }
    }

    private void updateModelSpinner(AIServerConfig config) {
        if (config != null) {
            String[] models = config.getModelArray();
            MarqueeSpinnerAdapter adapter = new MarqueeSpinnerAdapter(this,
                android.R.layout.simple_spinner_item, models);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            modelSpinner.setAdapter(adapter);
            
            // 如果有上次选择的模型，则选中它
            String lastSelectedModel = config.getLastSelectedModel();
            if (lastSelectedModel != null) {
                for (int i = 0; i < models.length; i++) {
                    if (models[i].equals(lastSelectedModel)) {
                        modelSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }
    }

    private void loadPrompts() {
        promptChipGroup.removeAllViews();
        List<Prompt> prompts = dbHelper.getAllPrompts();
        
        for (Prompt prompt : prompts) {
            Chip chip = new Chip(this);
            chip.setText(prompt.getTitle());
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                if (chatService != null) {
                    chatService.appendPrompt(prompt.getContent());
                }
            });
            promptChipGroup.addView(chip);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_prompts) {
            Intent intent = new Intent(this, PromptActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_update_anki_template) {
            // 显示确认对话框，提供更准确的信息和指导
            new AlertDialog.Builder(this)
                .setTitle("创建新Anki模板")
                .setMessage("这将创建一个新的「AI Chat」模板。要完全应用新模板，请按以下步骤操作：\n\n" +
                        "1. 创建新模板后，在AnkiDroid中打开「AI Chat」牌组\n" +
                        "2. 点击右上角菜单，选择「卡片浏览器」\n" +
                        "3. 筛选出使用旧模板的卡片\n" +
                        "4. 全选这些卡片，点击右上角菜单\n" +
                        "5. 选择「更改笔记类型」，将它们转移到新模板\n" +
                        "6. 重要：必须删除旧模板，否则新添加的卡片仍会使用旧模板\n\n" +
                        "确定要创建新模板吗？")
                .setPositiveButton("创建", (dialog, which) -> {
                    // 执行更新模板操作
                    updateAnkiTemplate();
                })
                .setNegativeButton("取消", null)
                .show();
            return true;
        } else if (item.getItemId() == R.id.action_theme) {
            DarkModeUtils.darkModeSettingDialog(MainActivity.this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 更新Anki模板的方法
    private void updateAnkiTemplate() {
        if (chatService != null) {
            // 在后台线程中执行
            new Thread(() -> {
                boolean success = chatService.updateAnkiTemplate();
                // 在UI线程显示结果
                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "Anki模板更新成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Anki模板更新失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        } else {
            Toast.makeText(this, "聊天服务未初始化", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkAnkiPermission() {
        if(!AnkiDroidHelper.hasPermission(this)) {
            AnkiDroidHelper.requestPermission(this, Constant.REQUEST_CODE_ANKI);
        }
    }

    private void loadAnkiDecks() {
        if (chatService == null) {
            Toast.makeText(this, "聊天服务未初始化", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading indicator
//        Toast.makeText(this, "正在加载牌组...", Toast.LENGTH_SHORT).show();
        
        // Load decks in background
        new Thread(() -> {
            String decksJson = chatService.getAnkiDecks();
            
            // Parse JSON
            try {
                Type type = new TypeToken<List<Map<String, Object>>>(){}.getType();
                allDeckList = new Gson().fromJson(decksJson, type);
                
                // Filter decks based on preferences
                filterDecks();
                
                // Update UI on main thread
                runOnUiThread(() -> {
                    updateDeckSpinner();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载牌组失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void filterDecks() {
        filteredDeckList.clear();
        
        for (Map<String, Object> deck : allDeckList) {
            long deckId = ((Number) deck.get("id")).longValue();
            if (!deckPreferences.isDeckHidden(deckId)) {
                filteredDeckList.add(deck);
            }
        }
    }
    
    private void updateDeckSpinner() {
        if (filteredDeckList.isEmpty()) {
            // If all decks are hidden, show a message
            if (!allDeckList.isEmpty()) {
                Toast.makeText(this, "所有牌组已被隐藏，请点击刷新按钮显示牌组", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未找到牌组", Toast.LENGTH_SHORT).show();
            }
            
            // Add a placeholder item
            List<String> placeholderList = new ArrayList<>();
            placeholderList.add("无可用牌组");
            
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, placeholderList);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            deckSpinner.setAdapter(adapter);
            return;
        }
        
        // Create adapter
        List<String> deckNames = new ArrayList<>();
        for (Map<String, Object> deck : filteredDeckList) {
            deckNames.add((String) deck.get("name"));
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, deckNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deckSpinner.setAdapter(adapter);
        
        // Restore selected deck
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        long savedDeckId = prefs.getLong(PREF_SELECTED_DECK_ID, -1);
        
        if (savedDeckId != -1) {
            // Find position of saved deck in filtered list
            for (int i = 0; i < filteredDeckList.size(); i++) {
                long deckId = ((Number) filteredDeckList.get(i).get("id")).longValue();
                if (deckId == savedDeckId) {
                    deckSpinner.setSelection(i);
                    break;
                }
            }
        }
        
        // Set listener
        deckSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < filteredDeckList.size()) {
                    long deckId = ((Number) filteredDeckList.get(position).get("id")).longValue();
                    chatService.setSelectedDeckId(deckId);
                    
                    // Save selection
                    SharedPreferences prefs = getPreferences(MODE_PRIVATE);
                    prefs.edit().putLong(PREF_SELECTED_DECK_ID, deckId).apply();
                    
                    Log.d("MainActivity", "Selected deck: " + filteredDeckList.get(position).get("name") + 
                        " (ID: " + deckId + ")");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        updateSelectionText();
        // Set initial Gone
        spinnerContainer.setVisibility(View.GONE);
    }
    
    private void showDeckFilterDialog() {
        // First load the latest decks
        if (chatService == null) {
            Toast.makeText(this, "聊天服务未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        
//        Toast.makeText(this, "正在加载牌组...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            String decksJson = chatService.getAnkiDecks();
            
            try {
                Type type = new TypeToken<List<Map<String, Object>>>(){}.getType();
                allDeckList = new Gson().fromJson(decksJson, type);
                
                runOnUiThread(() -> {
                    if (allDeckList.isEmpty()) {
                        Toast.makeText(this, "未找到牌组", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // Create dialog with checkboxes for each deck
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("选择要显示的牌组");
                    
                    // Create array of deck names and checked states
                    final String[] deckNames = new String[allDeckList.size()];
                    final boolean[] checkedItems = new boolean[allDeckList.size()];
                    
                    for (int i = 0; i < allDeckList.size(); i++) {
                        Map<String, Object> deck = allDeckList.get(i);
                        deckNames[i] = (String) deck.get("name");
                        long deckId = ((Number) deck.get("id")).longValue();
                        checkedItems[i] = !deckPreferences.isDeckHidden(deckId);
                    }
                    
                    builder.setMultiChoiceItems(deckNames, checkedItems, (dialog, which, isChecked) -> {
                        // Update checked state
                        checkedItems[which] = isChecked;
                    });
                    
                    // Add buttons
                    builder.setPositiveButton("确定", (dialog, which) -> {
                        // Update preferences based on checked state
                        for (int i = 0; i < allDeckList.size(); i++) {
                            long deckId = ((Number) allDeckList.get(i).get("id")).longValue();
                            deckPreferences.setDeckHidden(deckId, !checkedItems[i]);
                        }
                        
                        // Filter and update spinner
                        filterDecks();
                        updateDeckSpinner();
                    });
                    
                    builder.setNegativeButton("取消", null);
                    
                    // Add "Select All" and "Clear All" buttons
                    builder.setNeutralButton("全选/全不选", null);
                    
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    
                    // Override the neutral button to avoid dismissing the dialog
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                        // Check if any item is unchecked
                        boolean anyUnchecked = false;
                        for (boolean checked : checkedItems) {
                            if (!checked) {
                                anyUnchecked = true;
                                break;
                            }
                        }
                        
                        // Toggle all items
                        boolean newState = anyUnchecked;
                        for (int i = 0; i < checkedItems.length; i++) {
                            checkedItems[i] = newState;
                            ((AlertDialog) dialog).getListView().setItemChecked(i, newState);
                        }
                    });
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载牌组失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void handleSharedText(String text) {
        // Pass the text to your WebView or ChatService
        if (webView != null) {
            webView.evaluateJavascript(
                String.format("javascript:handleSharedText(`%s`);", text.replace("`", "\\`")),
                null
            );
        }
    }

    private void toggleSpinnerContainer() {
        if (isSpinnerContainerVisible % 2 == 0) {
            spinnerContainer.setVisibility(View.GONE);
//            indicatorBar.setBackgroundColor(getResources().getColor(R.color.btn_down, getTheme()));
//            promptHScrollView.setBackgroundColor(getResources().getColor(R.color.btn_down, getTheme()));
        } else if (isSpinnerContainerVisible % 2 == 1) {
//            promptHScrollView.setVisibility(View.GONE);
            spinnerContainer.setVisibility(View.VISIBLE);
//            indicatorBar.setBackgroundColor(getResources().getColor(R.color.btn_down, getTheme()));
        }
//        else if (isSpinnerContainerVisible % 3 == 2) {
//            spinnerContainer.setVisibility(View.VISIBLE);
//            promptHScrollView.setVisibility(View.VISIBLE);
//            indicatorBar.setBackgroundColor(getResources().getColor(R.color.green_container, getTheme()));
//            promptHScrollView.setBackgroundColor(getResources().getColor(R.color.green_container, getTheme()));
//        }
        isSpinnerContainerVisible = isSpinnerContainerVisible+1 % 2;
    }

    private void updateSelectionText() {
        String model = String.valueOf(modelSpinner.getSelectedItem());
        String deck = String.valueOf(deckSpinner.getSelectedItem());

        String text = String.format("%s | %s", model, deck);
        currentSelectionText.setText(text);
        currentSelectionText.setSelected(true); // Start marquee
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length == 0) {
            return;
        }

        if (requestCode == Constant.REQUEST_CODE_ANKI) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setMessage(R.string.permission_denied)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                openSettingsPage();
                            }
                        }).show();
            }
        }
    }

    private void openSettingsPage() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }
} 