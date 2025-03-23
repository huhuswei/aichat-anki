package com.ss.aianki;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.List;

public class PromptActivity extends AppCompatActivity {
    private DatabaseHelper dbHelper;
    private LinearLayout promptsContainer;
    private Prompt newPrompt;
    private View newPromptView;
    private boolean isAnyCardInEditMode = false;
    private FloatingActionButton fabAddPrompt;
    private boolean hasUnsavedNewCard = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prompt);
        
        // 启用返回按钮
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        dbHelper = new DatabaseHelper(this);
        promptsContainer = findViewById(R.id.promptsContainer);
        
        fabAddPrompt = findViewById(R.id.fabAddPrompt);
        fabAddPrompt.setOnClickListener(v -> showAddPromptDialog());
        
        loadPrompts();
    }

    private void loadPrompts() {
        promptsContainer.removeAllViews();
        List<Prompt> prompts = dbHelper.getAllPrompts();
        
        for (Prompt prompt : prompts) {
            View promptView = LayoutInflater.from(this)
                .inflate(R.layout.item_prompt, promptsContainer, false);
            
            setupPromptView(promptView, prompt, false); // Start in preview mode
            promptsContainer.addView(promptView);
        }
    }

    private void showAddPromptDialog() {
        if (hasUnsavedNewCard) {
            Toast.makeText(this, "请先保存当前新增的卡片", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isAnyCardInEditMode) {
            Toast.makeText(this, "请先完成当前卡片的编辑", Toast.LENGTH_SHORT).show();
            return;
        }
        
        View promptView = LayoutInflater.from(this)
            .inflate(R.layout.item_prompt, promptsContainer, false);
        Prompt newPrompt = new Prompt();
        setupPromptView(promptView, newPrompt, true);
        promptsContainer.addView(promptView, 0);
        hasUnsavedNewCard = true;
        isAnyCardInEditMode = true;
    }

    private void setupPromptView(View promptView, Prompt prompt, boolean isNew) {
        // Find all views
        LinearLayout editMode = promptView.findViewById(R.id.editMode);
        LinearLayout viewMode = promptView.findViewById(R.id.viewMode);
        TextInputLayout titleLayout = promptView.findViewById(R.id.promptTitleLayout);
        TextInputLayout contentLayout = promptView.findViewById(R.id.promptContentLayout);
        EditText titleEdit = promptView.findViewById(R.id.promptTitle);
        EditText contentEdit = promptView.findViewById(R.id.promptContent);
        Button btnDelete = promptView.findViewById(R.id.btnDelete);
        Button btnSave = promptView.findViewById(R.id.btnSave);
        TextView viewTitle = promptView.findViewById(R.id.viewTitle);
        TextView viewContent = promptView.findViewById(R.id.viewContent);
        Button btnCancel = promptView.findViewById(R.id.btnCancel);

        // Set initial content
        titleEdit.setText(prompt.getTitle());
        contentEdit.setText(prompt.getContent());
        viewTitle.setText(prompt.getTitle());
        viewContent.setText(prompt.getContent());

        // Set initial mode
        if (isNew) {
            editMode.setVisibility(View.VISIBLE);
            viewMode.setVisibility(View.GONE);
            titleEdit.requestFocus();
            
            // Use ViewTreeObserver to ensure the view is ready
            titleEdit.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    titleEdit.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        } else {
            editMode.setVisibility(View.GONE);
            viewMode.setVisibility(View.VISIBLE);
        }

        // Set up delete button click listener
        btnDelete.setOnClickListener(v -> {
            dbHelper.deletePrompt(prompt.getId());
            promptsContainer.removeView(promptView);
            hasUnsavedNewCard = false;
            isAnyCardInEditMode = false;
        });

        // Set up save button click listener
        btnSave.setOnClickListener(v -> {
            String title = titleEdit.getText().toString().trim();
            String content = contentEdit.getText().toString().trim();
            viewContent.setText(prompt.getContent());
            if (title.isEmpty() || content.isEmpty()) {
                Toast.makeText(this, "提示词标题和内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            // Switch to view mode
            prompt.setTitle(title);
            prompt.setContent(content);
            dbHelper.savePrompt(prompt);
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            // Update view mode content
            viewTitle.setText(prompt.getTitle());
            viewContent.setText(prompt.getContent());
            editMode.setVisibility(View.VISIBLE);
            // Switch to view mode
            editMode.setVisibility(View.GONE);
            viewMode.setVisibility(View.VISIBLE);
            titleEdit.requestFocus();
            isAnyCardInEditMode = false;
            hasUnsavedNewCard = false;
        });

        // Set up cancel button click listener
        btnCancel.setOnClickListener(v -> {
            if (isNew) {
                promptsContainer.removeView(promptView);
                hasUnsavedNewCard = false;
            } else {
                editMode.setVisibility(View.GONE);
                viewMode.setVisibility(View.VISIBLE);
                // Restore original values
                titleEdit.setText(prompt.getTitle());
                contentEdit.setText(prompt.getContent());
            }
            isAnyCardInEditMode = false;
        });

        // Set up long press listener to enter edit mode
        promptView.setOnLongClickListener(v -> {
            if (viewMode.getVisibility() == View.VISIBLE && isAnyCardInEditMode != true) {
                isAnyCardInEditMode = true;
                viewMode.setVisibility(View.GONE);
                editMode.setVisibility(View.VISIBLE);
                titleEdit.requestFocus();
            }
            return false;
        });
    }

    private void checkAndRemoveEmptyPrompt() {
        if (newPrompt != null && newPromptView != null) {
            TextInputEditText titleEdit = newPromptView.findViewById(R.id.promptTitle);
            TextInputEditText contentEdit = newPromptView.findViewById(R.id.promptContent);
            
            if ((titleEdit.getText() == null || titleEdit.getText().toString().trim().isEmpty()) &&
                (contentEdit.getText() == null || contentEdit.getText().toString().trim().isEmpty())) {
                promptsContainer.removeView(newPromptView);
                newPrompt = null;
                newPromptView = null;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (newPromptView != null) {
                Rect outRect = new Rect();
                newPromptView.getGlobalVisibleRect(outRect);
                
                if (!outRect.contains((int)ev.getRawX(), (int)ev.getRawY())) {
                    checkAndRemoveEmptyPrompt();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}