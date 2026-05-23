// 全局变量
let chatAndroidReady = false;
let isSingleTurnMode = false;
let isGenerating = false;
let userHasScrolled = false;
let currentMessageIndex = -1; // 当前显示的消息索引
let locatorInitialized = false; // 定位器是否已初始化

// 检查 ChatAndroid 是否可用
window.checkChatAndroid = function() {
    console.log('Checking ChatAndroid availability...');
    try {
        if (typeof ChatAndroid !== 'undefined') {
            chatAndroidReady = true;
            console.log('ChatAndroid is ready');
            
            // 检查对话模式
            checkConversationMode();
            
//            loadMessageHistory();
            return true;
        }
    } catch (e) {
        console.error('Error checking ChatAndroid:', e);
    }
    console.log('ChatAndroid not available');
    return false;
}

document.addEventListener('DOMContentLoaded', function() {
    const chatContainer = document.getElementById('chat-container');
    const messageInput = document.getElementById('message-input');
    const sendButton = document.getElementById('send-button');

    // 尽早检测状态栏高度
    updateStatusBarHeight();

    // Setup interrupt button (only call once)
    setupInterruptButton();

    // Settings gear is now in the top-right menu

    // 发送消息处理
    function sendMessage() {
        const messageInput = document.getElementById('message-input');
        var content = messageInput.value.trim();

        // 如果有附件，以 JSON 格式传递文本和文件数据
        var hasFiles = attachedFiles.length > 0;
        if (hasFiles) {
            var fileData = attachedFiles.map(function(f) {
                return { name: f.name, type: f.type, data: f.data };
            });
            content = JSON.stringify({ text: content, files: fileData });
        }

        if (content) {
            // Toggle to interrupt button
            isGenerating = true;
            toggleSendInterruptButtons(true);

            // Disable the input while generating
            messageInput.disabled = true;

            // Clear the input
            messageInput.value = '';

            // Call the Java method to send the message
            if (chatAndroidReady) {
                var container = document.getElementById('prompt-message');
                if (container) {
                    var prompt = container.querySelector('.prompt-content').textContent + '\n';
                    container.style.opacity = 0;
                    setTimeout(() => {
                        container.remove();
                    }, 300);
                    ChatAndroid.sendMessage(content, prompt);
                } else {
                    ChatAndroid.sendMessage(content);
                }
            }

            // 清空附件
            if (hasFiles) {
                attachedFiles = [];
                renderAttachments();
            }
        }
    }

    // 监听用户滚动事件
    chatContainer.addEventListener('scroll', function() {
        // 磨砂节流
        onChatScrollForFrost();

        // 检查内容是否满屏（即是否可以滚动）
        const isFullContent = chatContainer.scrollHeight > chatContainer.clientHeight;

        if (isFullContent && isGenerating) {
            // 内容满屏，可以滚动
            // 检查是否不是在底部
            if (chatContainer.scrollTop + chatContainer.clientHeight < chatContainer.scrollHeight - 50) {
                userHasScrolled = true;
            } else {
                // 如果滚动到底部，重置标志
                userHasScrolled = false;
            }
        } else {
            // 内容未满屏，无需处理滚动逻辑
            userHasScrolled = false;
        }
    });

    // 绑定事件监听器
    sendButton.addEventListener('click', sendMessage);

    // 初始检查
    checkChatAndroid();

    // 获取模态框元素
    const sessionsModal = document.getElementById('sessions-modal');

    // 点击模态框背景时关闭
    sessionsModal.addEventListener('click', function(e) {
        // 如果点击的是模态框本身（而不是内容区域）
        if (e.target === sessionsModal) {
            sessionsModal.style.display = 'none';
            document.getElementById('show-sessions-btn').textContent = '≡';
        }
    });

    // 点击关闭按钮时关闭
    const closeButton = sessionsModal.querySelector('.modal-close');
    if (closeButton) {
        closeButton.addEventListener('click', function() {
            sessionsModal.style.display = 'none';
        });
    }

    // Auto-resize textarea based on content
    setupTextareaAutoResize();
});

// 加载历史消息
function loadMessageHistory() {
    try {
        const historyJson = ChatAndroid.getMessageHistory();
        if (historyJson) {
            const history = JSON.parse(historyJson);
            history.forEach(message => {
                addMessageToUI(message);
            });
            scrollToBottom();
        }
    } catch (e) {
        console.error('Error loading message history:', e);
    }
}

// 添加新消息到 UI
function addMessage(message) {
    if (typeof ChatAndroid === 'undefined') {
        console.error('ChatAndroid not ready');
        return;
    }
    addMessageToUI(message);
    scrollToBottom();
}

// 添加消息到 UI
function addMessageToUI(message) {
    if (!message || !message.id) {
        console.error('Invalid message:', message);
        return;
    }
    if (message.role === 'system') {
        return;
    }
    
    const messageId = 'message-' + message.id;
    console.log('Adding message:', messageId);
    
    const chatContainer = document.getElementById('chat-container');
    let messageDiv = document.createElement('div');
    messageDiv.id = messageId;
    messageDiv.className = 'message ' + (message.role === 'user' ? 'user-message' : 'assistant-message markdown-body');
    messageDiv.dataset.messageId = message.id;  // 保存消息ID到DOM元素
    
    // 创建消息气泡
    const messageBubble = document.createElement('div');
    messageBubble.className = 'message-bubble';
    messageBubble.style.width = '100%';
    
    // 消息内容
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content markdown-body';

    // 检查是否是带文件附件的用户消息
    var displayContent = message.content || '';
    var fileAttachmentsHtml = '';
    if (message.role === 'user' && displayContent.indexOf('_hasFiles') > -1) {
        try {
            var fileMeta = JSON.parse(displayContent);
            displayContent = fileMeta._text || '';
            if (fileMeta._files && fileMeta._files.length > 0) {
                fileAttachmentsHtml = '<div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px">';
                fileMeta._files.forEach(function(f) {
                    if (f.type && f.type.startsWith('image/')) {
                        fileAttachmentsHtml += '<div class="attach-image" data-src="' + f.data + '" style="position:relative;width:80px;height:80px;border-radius:8px;overflow:hidden;border:1px solid rgba(128,128,128,0.2);flex-shrink:0;cursor:pointer">' +
                            '<img src="' + f.data + '" style="width:100%;height:100%;object-fit:cover;display:block">' +
                            '</div>';
                    } else {
                        fileAttachmentsHtml += '<div style="display:flex;align-items:center;gap:4px;padding:4px 10px;background:rgba(128,128,128,0.08);border-radius:6px;font-size:12px;color:#666;white-space:nowrap">' +
                            '📎 ' + f.name +
                            '</div>';
                    }
                });
                fileAttachmentsHtml += '</div>';
            }
        } catch (e) {}
    }

    contentDiv.innerHTML = formatMessage(displayContent) + fileAttachmentsHtml;
    messageBubble.appendChild(contentDiv);
    
    // 源代码编辑区域 - 放在消息气泡内部
    const sourceTextarea = document.createElement('textarea');
    sourceTextarea.className = 'source-editor';
    sourceTextarea.style.width = '88%';
//    sourceTextarea.style.minHeight = '200px';
//    sourceTextarea.style.height = 'auto'; // 允许自动调整高度
//    sourceTextarea.style.padding = '12px';
//    sourceTextarea.style.border = '1px solid #ddd';
//    sourceTextarea.style.borderRadius = '8px';
//    sourceTextarea.style.fontFamily = 'monospace';
//    sourceTextarea.style.fontSize = '14px';
//    sourceTextarea.style.lineHeight = '1.5';
//    sourceTextarea.style.resize = 'vertical';
//    sourceTextarea.style.boxSizing = 'border-box'; // 确保padding不会增加宽度
//    sourceTextarea.style.display = 'none';
//    sourceTextarea.style.overflow = 'auto'; // 确保内容过多时可以滚动
    messageBubble.appendChild(sourceTextarea);
    
    // 将气泡添加到消息容器
    messageDiv.appendChild(messageBubble);
    
    // 直接在这里设置原始内容属性
    messageDiv.setAttribute('data-raw-content', message.content || '');
    
    // 按钮容器
    if (message.role === 'assistant') {
        const buttonsDiv = document.createElement('div');
        buttonsDiv.className = 'message-buttons';
        
        // 创建保存/撤销按钮
        const saveButton = document.createElement('button');
        saveButton.className = 'message-button save-to-anki';
        saveButton.innerHTML = '💾';
        saveButton.title = '保存到Anki';
        
        // 检查是否已有 ankiNoteId
        if (message.ankiNoteId) {
            saveButton.innerHTML = '✖️';
            saveButton.title = '撤销保存';
            saveButton.classList.add('saved');
        }
        
        saveButton.onclick = function() {
            console.log('Toggle Anki save for message:', message.id);
            if (saveButton.classList.contains('saved')) {
                // 如果已保存，则撤销
                if (ChatAndroid.undoAnkiSave(message.id)) {
                    saveButton.innerHTML = '💾';
                    saveButton.title = '保存到Anki';
                    saveButton.classList.remove('saved');
                }
            } else {
                // 如果未保存，则保存
                if (ChatAndroid.saveToAnki(message.id)) {
                    saveButton.innerHTML = '✖️';
                    saveButton.title = '撤销保存';
                    saveButton.classList.add('saved');
                }
            }

            updateButtonsVisibility(messageDiv);
        };
        
        // 修改源代码切换按钮
        const toggleButton = document.createElement('button');
        toggleButton.className = 'message-button source-toggle';
        toggleButton.innerHTML = '📝';
        toggleButton.title = '查看源码';
        toggleButton.onclick = function(e) {
            e.stopPropagation(); // 阻止事件冒泡
            
            // 切换显示/编辑模式
            const isEditing = sourceTextarea.style.display === 'block';
            
            if (isEditing) {
                // 切换到显示模式
                sourceTextarea.style.display = 'none';
                contentDiv.style.display = 'block';
                toggleButton.innerHTML = '📝';
                toggleButton.title = '查看源码';
                saveSourceButton.style.display = 'none';
                
                // 恢复 Anki 按钮的显示
                const ankiButton = buttonsDiv.querySelector('.save-to-anki');
                if (ankiButton) {
                    ankiButton.style.display = 'inline-flex';
                }
            } else {
                // 切换到编辑模式
                // 获取最新内容
                try {
                    const rawContent = ChatAndroid.getMessageContent(message.id);
                    // 去除可能的前导换行符
                    sourceTextarea.value = rawContent.replace(/^\n+/, '');
                } catch (e) {
                    console.error('Error getting content:', e);
                    // 去除可能的前导换行符
                    sourceTextarea.value = (message.content || '').replace(/^\n+/, '');
                }
                
                // 调整编辑框高度与内容区域一致
                const contentHeight = contentDiv.offsetHeight;
                if (contentHeight > 200) {
                    sourceTextarea.style.minHeight = contentHeight + 'px';
                }
                
                contentDiv.style.display = 'none';
                sourceTextarea.style.display = 'block';
                sourceTextarea.style.cols = 80;
                toggleButton.innerHTML = '👁️';
                toggleButton.title = '查看效果';
                saveSourceButton.style.display = 'inline-flex';
                
                // 隐藏 Anki 按钮，只保留切换和保存按钮
                const ankiButton = buttonsDiv.querySelector('.save-to-anki');
                if (ankiButton) {
                    ankiButton.style.display = 'none';
                }
                
                // 聚焦并选中所有文本
                setTimeout(() => {
                    sourceTextarea.focus();
                    sourceTextarea.select();
                }, 100);
            }

            updateButtonsVisibility(messageDiv);

        };
        
        // 添加保存源码按钮
        const saveSourceButton = document.createElement('button');
        saveSourceButton.className = 'message-button save-source';
        saveSourceButton.innerHTML = '✓';
        saveSourceButton.title = '保存修改';
        saveSourceButton.style.display = 'none';
        saveSourceButton.onclick = function(e) {
            e.stopPropagation();

            // 获取编辑器内容
            const newContent = sourceTextarea.value;
            
            // 更新到后端
            ChatAndroid.updateMessageContent(message.id, newContent);
            
            // 更新显示内容
            contentDiv.innerHTML = formatMessage(newContent || '');
            
            // 重新渲染数学公式
            renderMathInElement(contentDiv, {
                delimiters: [
                    {left: '$$', right: '$$', display: true},
                    {left: '$', right: '$', display: false},
                    {left: '\\[', right: '\\]', display: true},
                    {left: '\\(', right: '\\)', display: false}
                ],
                throwOnError: false
            });

            try {
                if (typeof mermaid !== 'undefined') {
                    mermaid.run();  // 只使用一种方法，移除contentLoaded调用
                    console.log('Mermaid running');
                } else {
                    console.log('Mermaid not available');
                }
            } catch (e) {
                console.log('Error running mermaid:', e);
            }

            // 处理代码高亮
            contentDiv.querySelectorAll('pre code').forEach((block) => {
                hljs.highlightElement(block);
            });

            // 更新原始内容属性
            messageDiv.setAttribute('data-raw-content', newContent);
            
            // 切换回显示模式
            sourceTextarea.style.display = 'none';
            contentDiv.style.display = 'block';
            toggleButton.innerHTML = '📝';
            toggleButton.title = '查看源码';
            saveSourceButton.style.display = 'none';
            
            // 恢复 Anki 按钮的显示
            const ankiButton = buttonsDiv.querySelector('.save-to-anki');
            if (ankiButton) {
                ankiButton.style.display = 'inline-flex';
            }
            
            // 显示成功提示
            showToast('修改已保存');

            updateButtonsVisibility(messageDiv);
        };
        
        // 添加删除按钮
        const deleteButton = document.createElement('button');
        deleteButton.className = 'message-button delete-message';
        deleteButton.innerHTML = '🗑️';
        deleteButton.title = '删除消息';
        deleteButton.onclick = function(e) {
            e.stopPropagation();

            // 使用自定义确认弹窗代替 confirm（Android WebView 不支持原生 confirm）
            showConfirmDialog('确定要删除这条消息吗？', function() {
                if (ChatAndroid.deleteMessage(message.id)) {
                    // 删除成功，前端会通过 removeMessageFromUI 更新
                } else {
                    showToast('删除失败', true);
                }
            });
        };

        // 添加按钮到按钮容器
        buttonsDiv.appendChild(saveButton);
        buttonsDiv.appendChild(saveSourceButton);
        buttonsDiv.appendChild(toggleButton);
        buttonsDiv.appendChild(deleteButton);

        messageDiv.appendChild(buttonsDiv);
    }
    
    chatContainer.appendChild(messageDiv);
    
    // 立即渲染数学公式
    if (ChatAndroid.getOutputFormat() === 'Markdown') {
        renderMathInElement(contentDiv, {
            delimiters: [
                {left: '$$', right: '$$', display: true},
                {left: '$', right: '$', display: false},
                {left: '\\[', right: '\\]', display: true},
                {left: '\\(', right: '\\)', display: false}
            ],
            throwOnError: false,
            output: 'html',
            strict: false
        });
    }

        try {
            if (typeof mermaid !== 'undefined') {
                mermaid.run();  // 只使用一种方法，移除contentLoaded调用
                console.log('Mermaid running');
            } else {
                console.log('Mermaid not available');
            }
        } catch (e) {
            console.log('Error running mermaid:', e);
        }
    
    console.log('Created new message element:', messageId);
    scrollToBottom();

    // 更新消息定位器
    updateMessageLocator();

    // 添加触摸事件监听
    messageDiv.addEventListener('click', function(e) {
        // 如果点击的是按钮，不处理
        if (e.target.closest('.message-buttons')) {
            return;
        }

        // 如果点击的是图片，不阻止冒泡（交给 Viewer.js 处理）
        if (e.target.closest('.message-content img') || e.target.closest('.attach-image')) {
            return;
        }

        e.stopPropagation();  // 阻止冒泡，避免触发全局click处理器

        // 移除之前的标记
        document.querySelectorAll('.message.last-clicked').forEach(msg => {
            msg.classList.remove('last-clicked');
        });
        
        // 隐藏所有其他消息的按钮
        document.querySelectorAll('.message-buttons').forEach(buttons => {
            if (buttons !== this.querySelector('.message-buttons')) {
                buttons.style.opacity = '0';
                buttons.style.pointerEvents = 'none';
            }
        });

        // 切换当前消息的按钮显示状态
        const buttonsDiv = this.querySelector('.message-buttons');
        if (buttonsDiv) {
            const isVisible = buttonsDiv.style.opacity === '1';
            if (!isVisible) {
                // 添加标记
                this.classList.add('last-clicked');
                updateButtonsVisibility(this);
            } else {
                // 移除标记
                this.classList.remove('last-clicked');
                buttonsDiv.style.opacity = '0';
                buttonsDiv.style.pointerEvents = 'none';
            }
        }
    });

    // 添加事件阻止冒泡，防止点击编辑框时触发消息点击事件
    sourceTextarea.onclick = function(e) {
        e.stopPropagation();
    };

    // 添加事件阻止冒泡，防止点击编辑框时触发消息点击事件
    sourceTextarea.onfocus = function(e) {
        e.stopPropagation();
        
        // 确保按钮保持可见
        const buttonsDiv = messageDiv.querySelector('.message-buttons');
        if (buttonsDiv) {
            // 计算按钮位置
            updateButtonsVisibility(messageDiv);
            
            // 标记为最后点击的消息
            document.querySelectorAll('.message').forEach(msg => {
                msg.classList.remove('last-clicked');
            });
            messageDiv.classList.add('last-clicked');
        }
    };

    if (message.role === 'user') {
        const resendButton = createResendButton(message.id);
        messageDiv.insertBefore(resendButton, messageBubble);

        // Add click handler to toggle resend button visibility
        messageDiv.addEventListener('click', function() {
            // Only show resend button if not currently generating
            if (!isGenerating) {
                const buttons = this.querySelectorAll('.resend-button');
                buttons.forEach(button => {
                    button.style.display = button.style.display === 'none' ? 'block' : 'none';
                });
            }
        });
    }
}

// 更新消息
function updateMessage(message) {
    if (!message || !message.id) {
        console.error('Invalid message:', message);
        return;
    }
    
    console.log('Updating message:', message);  // 添加日志
    const messageId = 'message-' + message.id;
    const messageElement = document.getElementById(messageId);
    
    if (messageElement) {
        const contentElement = messageElement.querySelector('.message-content');
        const sourceElement = messageElement.querySelector('.message-source');
        
        if (contentElement && sourceElement) {
            const formattedContent = formatMessage(message.content);
            contentElement.innerHTML = formattedContent;
            sourceElement.textContent = message.content;
            
            // 立即渲染数学公式
            if (ChatAndroid.getOutputFormat() === 'Markdown') {
                renderMathInElement(contentElement, {
                    delimiters: [
                        {left: '$$', right: '$$', display: true},
                        {left: '$', right: '$', display: false},
                        {left: '\\[', right: '\\]', display: true},
                        {left: '\\(', right: '\\)', display: false}
                    ],
                    throwOnError: false,
                    output: 'html',
                    strict: false
                });
            }
            
            scrollToBottom();
        }
    } else {
        console.error('Message element not found:', messageId);
    }
}

// 格式化消息内容
function formatMessage(content) {
    if (!content) return '';

    try {
        // Replace <think> tags with details/summary
        content = content.replace(/<think>([\s\S]*?)<\/think>/g, "<details markdown='1'><summary>think</summary>$1</details>");
        content = processAudioTags(content);

        // Save math expressions
        const mathExpressions = [];
        const preserved = { mermaid: [], latex: [], code: [] };
        let processedContent = content
            .replace(/(\$\$[\s\S]*?\$\$|\$[\s\S]*?\$|\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\))/g, (match, p1, offset) => {
                preserved.latex.push(p1);
                return `@@MATH_EXPR_${preserved.latex.length - 1}@@`;
            })
            .replace(/```mermaid\s*?\n([\s\S]*?)```/g, (_, code) => {
                          preserved.mermaid.push(code.trim());
                          return `Ⓜ️${preserved.mermaid.length-1}Ⓜ️`;
                        });

        // Use marked to process Markdown
        processedContent = marked.parse(processedContent, {
            mangle: false,
            headerIds: false,
            sanitize: false,
            breaks: true,
            gfm: true
        });

        // Restore math expressions
        processedContent = processedContent
            .replace(/@@MATH_EXPR_(\d+)@@/g, (match, index) => {
                return preserved.latex[parseInt(index)];
            })
            .replace(/Ⓜ️(\d+)Ⓜ️/g, (_, id) =>
                          `<pre class="mermaid">${preserved.mermaid[id]}</pre>`);

        console.log("Mermaid " + processedContent);
        console.log("Mermaid " + processedContent);
        return processedContent;
    } catch (e) {
        console.error('Error formatting message:', e);
        return content;
    }
}

// 滚动到底部
function scrollToBottom() {
    // 如果用户没有手动滚动，则自动滚动到底部
    if (!userHasScrolled) {
        const chatContainer = document.getElementById('chat-container');
        if (chatContainer) {
            chatContainer.scrollTop = chatContainer.scrollHeight;
        }
    }
}

// 清空消息
function clearMessages() {
    const chatContainer = document.getElementById('chat-container');
    chatContainer.innerHTML = '';
}

// 重新加载消息
function reloadMessages(messages) {
    clearMessages();
    messages.forEach(message => {
        addMessageToUI(message);
    });
    scrollToBottom();
}

// 初始化会话管理
document.getElementById('new-session-btn').onclick = function() {
//    if (confirm('确定要开始新会话吗？当前会话将被保存。')) {
        interruptAction();
        ChatAndroid.newSession();
//    }
};

// 修改历史会话按钮的点击事件
document.getElementById('show-sessions-btn').onclick = function() {
    const modal = document.getElementById('sessions-modal');
    const isVisible = modal.style.display === 'flex';
    
    if (isVisible) {
        // 如果当前可见，则隐藏
        modal.style.display = 'none';
        this.textContent = '历史会话';
    } else {
        // 如果当前隐藏，则显示并刷新列表
        const sessionsList = document.getElementById('sessions-list');
        const sessions = JSON.parse(ChatAndroid.getSessionList());
        updateSessionsList(sessions);
        modal.style.display = 'flex';
        this.textContent = '≡';
    }
};

// prompt panel
document.getElementById('toggle-config-btn').onclick = function() {
//    if (confirm('确定要开始新会话吗？当前会话将被保存。')) {
        MainAndroid.toggleSimpleUI();
//    }
};

// ===== 浮动菜单（已移至 menu.js） =====

// 修改关闭按钮的点击事件
document.querySelector('.modal-close').onclick = function() {
    document.getElementById('sessions-modal').style.display = 'none';
    document.getElementById('show-sessions-btn').textContent = '≡';
};

// 加载会话
function loadSession(sessionId) {
    const modal = document.getElementById('sessions-modal');
    const showSessionsBtn = document.getElementById('show-sessions-btn');

    // 隐藏模态框
    modal.style.display = 'none';
    // 更新按钮文本
    showSessionsBtn.textContent = '≡';

    interruptAction();
    ChatAndroid.loadSession(sessionId);

}

function getFirstSessionId() {
    try {
        // 获取所有会话（空查询获取全部会话）
        const sessions = JSON.parse(ChatAndroid.searchSessions(''));

        if (sessions && sessions.length > 0) {
            // 获取第一个会话
            const firstSession = sessions[0];
            // 添加安全检查
            return firstSession && firstSession.id ? firstSession.id : "";
        } else {
            return "";
        }
    } catch (error) {
        // 处理JSON解析错误
        console.error('Error parsing sessions:', error);
        return "";
    }
}

// 搜索会话
document.getElementById('session-search').addEventListener('input', function(e) {
    const query = e.target.value;
    const sessions = JSON.parse(ChatAndroid.searchSessions(query));
    updateSessionsList(sessions);
});

// 更新会话列表
function updateSessionsList(sessions) {
    const sessionsList = document.getElementById('sessions-list');
    sessionsList.innerHTML = sessions.map(session => `
        <div class="session-item">
            <div class="session-content" onclick="loadSession('${session.id}');">
                <div class="session-title">${session.title}</div>
                <div class="session-meta">
                    <span class="session-time">${new Date(session.timestamp).toLocaleString()}</span>
                    <span class="message-count">${session.messageCount} 条消息</span>
                </div>
            </div>
            <button class="session-delete" onclick="deleteSession('${session.id}', event)" title="删除会话">
                <span>×</span>
            </button>
        </div>
    `).join('');
}

// 加载首个session
function loadFirstSession() {
    // 获取所有会话（空查询获取全部会话）
    const sessions = JSON.parse(ChatAndroid.searchSessions(''));

    if (sessions && sessions.length > 0) {
        // 获取第一个会话
        const firstSession = sessions[0];
        // 加载该会话
        ChatAndroid.loadSession(firstSession.id);
    } else {
        // 如果没有会话，可以创建一个新会话或者显示提示
        console.log('没有找到任何会话');
        // 可选：创建一个新会话
        // createNewSession();
    }
}

// 页面加载完成后自动加载首个会话
document.addEventListener('DOMContentLoaded', function() {
    loadFirstSession();
});

// 删除会话
function deleteSession(sessionId, event) {
    event.stopPropagation();

   if (ChatAndroid.deleteSession(sessionId)) {
       // 删除成功后刷新列表
       const sessions = JSON.parse(ChatAndroid.getSessionList());
       updateSessionsList(sessions);

       // 显示提示
       showToast('会话已删除');
   } else {
       showToast('删除失败，请重试', true);
   }
//    if (confirm('确定要删除这个会话吗？此操作不可恢复。')) {
//        if (ChatAndroid.deleteSession(sessionId)) {
//            // 删除成功后刷新列表
//            const sessions = JSON.parse(ChatAndroid.getSessionList());
//            updateSessionsList(sessions);
//
//            // 显示提示
//            showToast('会话已删除');
//        } else {
//            showToast('删除失败，请重试', true);
//        }
//    }
}

// 显示自定义确认弹窗（替代 confirm，因为 Android WebView 不支持原生 confirm）
function showConfirmDialog(message, onConfirm) {
    const overlay = document.createElement('div');
    overlay.className = 'confirm-overlay';
    overlay.onclick = function(e) {
        if (e.target === overlay) overlay.remove();
    };

    const dialog = document.createElement('div');
    dialog.className = 'confirm-dialog';

    const msgDiv = document.createElement('div');
    msgDiv.className = 'confirm-message';
    msgDiv.textContent = message;

    const btnGroup = document.createElement('div');
    btnGroup.className = 'confirm-buttons';

    const cancelBtn = document.createElement('button');
    cancelBtn.className = 'confirm-btn confirm-cancel';
    cancelBtn.textContent = '取消';
    cancelBtn.onclick = function() { overlay.remove(); };

    const okBtn = document.createElement('button');
    okBtn.className = 'confirm-btn confirm-ok';
    okBtn.textContent = '确定';
    okBtn.onclick = function() {
        overlay.remove();
        if (onConfirm) onConfirm();
    };

    btnGroup.appendChild(cancelBtn);
    btnGroup.appendChild(okBtn);
    dialog.appendChild(msgDiv);
    dialog.appendChild(btnGroup);
    overlay.appendChild(dialog);
    document.body.appendChild(overlay);
}

// 显示 Toast 提示
function showToast(message, isError = false) {
    const toast = document.createElement('div');
    toast.className = `toast ${isError ? 'error' : ''}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 2000);
}

// 添加 Toast 样式
const style = document.createElement('style');
style.textContent = `
    .toast {
        position: fixed;
        bottom: 20px;
        left: 50%;
        transform: translateX(-50%);
        background: rgba(0, 0, 0, 0.7);
        color: white;
        padding: 8px 16px;
        border-radius: 4px;
        z-index: 1000;
        animation: fadeInOut 2s ease;
    }
    
    .toast.error {
        background: rgba(255, 0, 0, 0.7);
    }
    
    @keyframes fadeInOut {
        0% { opacity: 0; transform: translate(-50%, 20px); }
        10% { opacity: 1; transform: translate(-50%, 0); }
        90% { opacity: 1; transform: translate(-50%, 0); }
        100% { opacity: 0; transform: translate(-50%, -20px); }
    }
`;
document.head.appendChild(style);

// 将 isElementInViewport 函数移到全局作用域
function isElementInViewport(el) {
    const rect = el.getBoundingClientRect();
    const inputContainer = document.getElementById('input-container');
    const inputRect = inputContainer.getBoundingClientRect();
    
    return (
        rect.top >= 0 &&
        rect.left >= 0 &&
        rect.bottom <= (inputRect.top || window.innerHeight) &&
        rect.right <= window.innerWidth
    );
}

// 更新按钮位置和可见性的函数
function updateButtonsVisibility(messageDiv) {
    const buttonsDiv = messageDiv.querySelector('.message-buttons');
    if (!buttonsDiv) return;

    // 检查消息是否在可见范围内
    const rect = messageDiv.getBoundingClientRect();
    const inputContainer = document.getElementById('input-container');
    const inputRect = inputContainer.getBoundingClientRect();
    
    // 如果消息不在可见范围内，隐藏按钮
    if (rect.bottom < 0 || rect.top > inputRect.top || rect.right < 0 || rect.left > window.innerWidth) {
        buttonsDiv.style.opacity = '0';
        buttonsDiv.style.pointerEvents = 'none';
        return;
    }
    
    const isUserMessage = messageDiv.classList.contains('user-message');
    
    // 计算按钮位置
    if (isUserMessage) {
        buttonsDiv.style.left = (rect.left - 40) + 'px';
    } else {
        buttonsDiv.style.left = (rect.right - 1) + 'px';
    }
    
    // 计算按钮高度和消息高度
    const buttonHeight = buttonsDiv.offsetHeight;
    const messageHeight = rect.height;
    
    // 计算垂直居中位置
    let top = rect.top + (messageHeight - buttonHeight) / 2;
    
    // 确保按钮不会超出视窗顶部和输入框
    const maxTop = inputRect.top - buttonHeight - 8;
    var statusH = parseInt(getComputedStyle(document.documentElement).getPropertyValue('--status-bar-height')) || 0;
    const minTop = statusH + 8;
    
    // 调整最终位置
    let adjustedTop = Math.max(minTop, Math.min(maxTop, top));
    
    buttonsDiv.style.top = adjustedTop + 'px';
    buttonsDiv.style.opacity = '1';
    buttonsDiv.style.pointerEvents = 'auto';
}

// 修改 document.addEventListener('click') 事件处理
document.addEventListener('click', function(e) {
    // 如果点击的是消息或其子元素
    const messageDiv = e.target.closest('.message');
    const buttonsDiv = messageDiv ? messageDiv.querySelector('.message-buttons') : null;
    if (messageDiv && buttonsDiv && buttonsDiv.style.opacity == '0') {
        // 检查是否在编辑模式
        const sourceTextarea = messageDiv.querySelector('.source-editor');
        const isEditing = sourceTextarea && sourceTextarea.style.display === 'block';
        
        // 如果在编辑模式，不要隐藏按钮
        if (isEditing) {
            return;
        }
        
        // 标记为最后点击的消息
        document.querySelectorAll('.message').forEach(msg => {
            msg.classList.remove('last-clicked');
        });
        messageDiv.classList.add('last-clicked');
        
        // 更新按钮位置和可见性
        updateButtonsVisibility(messageDiv);
    } else {
        // 点击了消息外部，隐藏所有按钮
        document.querySelectorAll('.message-buttons').forEach(buttons => {
            buttons.style.opacity = '0';
            buttons.style.pointerEvents = 'none';
        });
    }
});

// 简化版：滚动时实时更新定位器
document.getElementById('chat-container').addEventListener('scroll', function() {
    const chatContainer = this;
    const scrollTop = chatContainer.scrollTop;

    // 隐藏按钮
    document.querySelectorAll('.message-buttons').forEach(buttons => {
        buttons.style.opacity = '0';
        buttons.style.pointerEvents = 'none';
    });

    // 更新滚动进度条
    updateScrollProgress();

    // 获取所有消息
    const messages = chatContainer.querySelectorAll('.message');
    if (messages.length <= 1) {
        hideLocator();
        return;
    }

    // 计算视口区域
    const viewportTop = scrollTop;
    const viewportBottom = scrollTop + chatContainer.clientHeight;

    // 找到视口内最靠上的消息
    let bestIndex = 0;
    let minTop = Infinity;

    messages.forEach((message, index) => {
        const msgTop = message.offsetTop;
        const msgBottom = msgTop + message.offsetHeight;

        // 检查消息是否在视口内（至少有一部分可见）
        if (msgBottom > viewportTop && msgTop < viewportBottom) {
            // 计算消息顶部到视口顶部的距离
            const distFromTop = msgTop - viewportTop;
            // 优先选择视口内最靠上的消息
            if (distFromTop < minTop) {
                minTop = distFromTop;
                bestIndex = index;
            }
        }
    });

    // 如果没有消息在视口内（罕见情况），选择离视口最近的消息
    if (minTop === Infinity) {
        let minDistance = Infinity;
        messages.forEach((message, index) => {
            const msgTop = message.offsetTop;
            const msgBottom = msgTop + message.offsetHeight;
            const msgCenter = (msgTop + msgBottom) / 2;
            const distance = Math.abs(msgCenter - viewportTop);

            if (distance < minDistance) {
                minDistance = distance;
                bestIndex = index;
            }
        });
    }

    // 更新全局索引
    currentMessageIndex = bestIndex;

    // 立即更新定位器
    updateLocatorRealtime(messages, currentMessageIndex);
});

// 实时更新定位器函数
function updateLocatorRealtime(messages, activeIndex) {
    // 检查是否启用了定位器
    var showLocator = localStorage.getItem('chat_show_locator');
    if (showLocator === 'false') {
        return; // 用户关闭了定位器，不更新
    }

    let locator = document.getElementById('message-locator');

    // 如果定位器不存在，创建它
    if (!locator) {
        // 再次检查设置
        if (localStorage.getItem('chat_show_locator') === 'false') {
            return;
        }
        locator = document.createElement('div');
        locator.className = 'message-locator';
        locator.id = 'message-locator';
        document.body.appendChild(locator);
    }

    // 重建所有定位点
    locator.innerHTML = '';
    messages.forEach((message, index) => {
        const dot = document.createElement('button');
        dot.className = 'locator-dot';
        dot.dataset.index = index;

        // 添加用户/助手类型
        if (message.classList.contains('user-message')) {
            dot.classList.add('user');
        } else {
            dot.classList.add('assistant');
        }

        // 设置 active 状态
        if (index === activeIndex) {
            dot.classList.add('active');
        }

        // 点击事件
        dot.onclick = function(e) {
            e.stopPropagation();
            scrollToMessage(index);
        };

        locator.appendChild(dot);
    });

    // 显示定位器
    locator.classList.add('visible');

    // 检查是否需要添加"滚动到底部"按钮
    const chatContainer = document.getElementById('chat-container');
    if (chatContainer.scrollHeight > chatContainer.clientHeight + 100) {
        // 内容过长，添加到底部按钮
        const bottomDot = document.createElement('button');
        bottomDot.className = 'locator-dot locator-bottom';
        bottomDot.title = '滚动到底部';
        bottomDot.onclick = function(e) {
            e.stopPropagation();
            chatContainer.scrollTo({
                top: chatContainer.scrollHeight,
                behavior: 'smooth'
            });
        };
        locator.appendChild(bottomDot);
    }

    // 3秒后隐藏
    if (locatorHideTimer) clearTimeout(locatorHideTimer);
    locatorHideTimer = setTimeout(() => {
        locator.classList.remove('visible');
    }, 3000);
}

// 实时更新定位器高亮（只更新 dot 的 active 状态，不重建 DOM）
// 注意：此函数现在主要在滚动事件中直接处理，这里保留作为备用
function updateLocatorActiveDot() {
    const chatContainer = document.getElementById('chat-container');
    if (!chatContainer) return;

    const messages = chatContainer.querySelectorAll('.message');
    if (messages.length <= 1) {
        hideLocator();
        return;
    }

    let locator = document.getElementById('message-locator');
    if (!locator) return;

    const dots = locator.querySelectorAll('.locator-dot');
    dots.forEach((dot, index) => {
        if (index === currentMessageIndex) {
            dot.classList.add('active');
        } else {
            dot.classList.remove('active');
        }
    });

    // 确保定位器可见
    locator.classList.add('visible');
}

// 实时更新定位器高亮
function updateLocatorHighlight() {
    const locator = document.getElementById('message-locator');
    if (!locator) return;

    const dots = locator.querySelectorAll('.locator-dot');
    dots.forEach((dot, index) => {
        if (index === currentMessageIndex) {
            dot.classList.add('active');
        } else {
            dot.classList.remove('active');
        }
    });
} 

// 修改 updateMessageContent 函数
function updateMessageContent(messageId, content) {
    const messageDiv = document.getElementById('message-' + messageId);
    if (messageDiv) {
        const contentDiv = messageDiv.querySelector('.message-content');
        if (contentDiv) {
            // 使用修改后的 formatMessage 处理内容
            contentDiv.innerHTML = formatMessage(content);
            
            // 使用 KaTeX 渲染数学公式
            renderMathInElement(contentDiv, {
                delimiters: [
                    {left: '$$', right: '$$', display: true},
                    {left: '$', right: '$', display: false},
                    {left: '\\[', right: '\\]', display: true},
                    {left: '\\(', right: '\\)', display: false}
                ],
                throwOnError: false,
                output: 'html',
                strict: false
            });

            try {
                if (typeof mermaid !== 'undefined') {
                    mermaid.run();  // 只使用一种方法，移除contentLoaded调用
                    console.log('Mermaid running');
                } else {
                    console.log('Mermaid not available');
                }
            } catch (e) {
                console.log('Error running mermaid:', e);
            }

            // 处理代码高亮
            contentDiv.querySelectorAll('pre code').forEach((block) => {
                hljs.highlightElement(block);
            });
            
            // 滚动到底部
            scrollToBottom();
        }
    }
}

// 从 UI 中删除消息
function removeMessageFromUI(messageId) {
    const messageDiv = document.getElementById('message-' + messageId);
    if (messageDiv) {
        // 添加淡出动画
        messageDiv.style.transition = 'opacity 0.3s, transform 0.3s';
        messageDiv.style.opacity = '0';
        messageDiv.style.transform = 'scale(0.9)';

        // 动画结束后移除元素
        setTimeout(() => {
            messageDiv.remove();

            // 隐藏当前显示的按钮（因为消息已被删除）
            document.querySelectorAll('.message-buttons').forEach(buttons => {
                buttons.style.opacity = '0';
                buttons.style.pointerEvents = 'none';
            });

            // 更新消息定位器
            updateMessageLocator();
        }, 300);
    }
}

// 添加 marked 配置
marked.setOptions({
    highlight: function(code, lang) {
        if (lang && hljs.getLanguage(lang)) {
            try {
                return hljs.highlight(code, { language: lang }).value;
            } catch (err) {}
        }
        return code;
    },
    breaks: true,
    gfm: true
});

// 响应完成时的回调
function onResponseComplete() {
    console.log("Response complete");
    isGenerating = false;
    toggleSendInterruptButtons(false);
    
    // Re-enable the input
    document.getElementById('message-input').disabled = false;

    // 重置滚动状态，恢复自动滚动
    userHasScrolled = false;

    // Update all assistant messages to replace think tags
    const assistantMessages = document.querySelectorAll('.assistant-message');
    assistantMessages.forEach(messageDiv => {
        const contentDiv = messageDiv.querySelector('.message-content');
        const sourceTextarea = messageDiv.querySelector('.source-editor');
        const rawContent = messageDiv.getAttribute('data-raw-content');
        
        if (contentDiv && sourceTextarea && rawContent) {
            // Replace think tags in the raw content
            const updatedContent = rawContent.replace(/<think>([\s\S]*?)<\/think>/g, "<details markdown='1'><summary>think</summary>$1</details>");
            
            // Update the content div and source textarea
            contentDiv.innerHTML = formatMessage(updatedContent);
            sourceTextarea.value = updatedContent;

            // 使用 KaTeX 渲染数学公式
            renderMathInElement(contentDiv, {
                delimiters: [
                    {left: '$$', right: '$$', display: true},
                    {left: '$', right: '$', display: false},
                    {left: '\\[', right: '\\]', display: true},
                    {left: '\\(', right: '\\)', display: false}
                ],
                throwOnError: false,
                output: 'html',
                strict: false
            });

            try {
                if (typeof mermaid !== 'undefined') {
                    mermaid.run();  // 只使用一种方法，移除contentLoaded调用
                    console.log('Mermaid running');
                } else {
                    console.log('Mermaid not available');
                }
            } catch (e) {
                console.log('Error running mermaid:', e);
            }
            
            // Update the raw content attribute
            messageDiv.setAttribute('data-raw-content', updatedContent);
        }
    });
}

// 添加一个直接的调试函数来检查源码内容
function debugSourceContent(messageId) {
    const messageDiv = document.getElementById('message-' + messageId);
    if (!messageDiv) {
        console.error('Message div not found:', messageId);
        return;
    }
    
    const rawContent = messageDiv.getAttribute('data-raw-content');
    console.log('Raw content attribute:', rawContent);
    console.log('Message div HTML:', messageDiv.innerHTML);
    
    // 直接在页面上显示原始内容，用于调试
    alert('Raw content: ' + rawContent);
}

// ===== 公式识别按钮 =====
// 提示词显隐按钮
document.getElementById('toggle-prompts-btn').addEventListener('click', function() {
    var pc = document.getElementById('prompt-container');
    if (!pc) return;
    var hidden = pc.style.display === 'none';
    pc.style.display = hidden ? '' : 'none';
    this.style.opacity = hidden ? '1' : '0.4';
    localStorage.setItem('chat_prompts_hidden', hidden ? 'false' : 'true');
    // 隐藏/显示 prompt 时调整 message-input 的上边距
    var msgInput = document.getElementById('message-input');
    if (msgInput) {
        msgInput.style.marginTop = hidden ? '8px' : '4px';
    }
});

document.getElementById('formula-btn').addEventListener('click', function() {
    if (typeof MainAndroid !== 'undefined') {
        MainAndroid.openFormulaActivity();
    } else {
        console.error('MainAndroid not available');
    }
});

// 处理公式识别返回的文本
function handleFormulaText(text) {
    const input = document.getElementById('message-input');
    if (input) {
        input.value = text;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.focus();
        // 将光标移动到末尾
        const len = input.value.length;
        input.setSelectionRange(len, len);
    }
}

// 初始化对话模式切换按钮
document.getElementById('toggle-mode-button').addEventListener('click', function() {
    isSingleTurnMode = !isSingleTurnMode;
    updateModeIndicator();
    
    // 调用 ChatAndroid 设置对话模式
    if (chatAndroidReady) {
        ChatAndroid.setConversationMode(isSingleTurnMode ? "single" : "multi");
    }
    
    // 不再清除消息历史，只更新UI状态
    // 如果切换到单轮模式，不做任何清除操作
});

// 更新模式指示器
function updateModeIndicator() {
    const modeIndicator = document.getElementById('mode-indicator');
    modeIndicator.textContent = isSingleTurnMode ? '⇆' : '⤻';
    
    // 更新文档类，以便应用不同的样式
    if (isSingleTurnMode) {
        document.body.classList.add('single-turn-mode');
    } else {
        document.body.classList.remove('single-turn-mode');
    }
}

// 在初始化时检查当前模式
function checkConversationMode() {
    if (chatAndroidReady) {
        const mode = ChatAndroid.getConversationMode();
        isSingleTurnMode = (mode === "single");
        updateModeIndicator();
    }
}

function toggleSendInterruptButtons(showInterrupt) {
    console.log("toggleSendInterruptButtons called with:", showInterrupt);
    const sendButton = document.getElementById('send-button');
    const interruptBtn = document.getElementById('interruptBtn');
    
    if (sendButton && interruptBtn) {
        if (showInterrupt) {
            // Show interrupt button, hide send button
            sendButton.style.display = 'none';
            interruptBtn.style.display = 'block';
        } else {
            // Show send button, hide interrupt button
            sendButton.style.display = 'block';
            interruptBtn.style.display = 'none';
        }
        console.log("Buttons toggled - Send:", sendButton.style.display, "Interrupt:", interruptBtn.style.display);
    } else {
        console.error("One or both buttons not found when trying to toggle");
    }
}

function setupInterruptButton() {
    console.log("Setting up interrupt button");
    const interruptBtn = document.getElementById('interruptBtn');
    if (interruptBtn) {
        console.log("Interrupt button found");
        interruptBtn.addEventListener('click', function() {
        interruptAction();
        });
    } else {
        console.error("Interrupt button not found in the DOM");
    }
}

function interruptAction() {
    console.log("Interrupt button clicked, isGenerating:", isGenerating);
    if (isGenerating) {
        console.log("Calling ChatAndroid.interruptResponse()");
        ChatAndroid.interruptResponse();
        toggleSendInterruptButtons(false);
        isGenerating = false;

        // Re-enable the input
        document.getElementById('message-input').disabled = false;

        // 重置滚动状态，恢复自动滚动
        userHasScrolled = false;

        // 确保最后滚动到底部
        scrollToBottom();
    }
}
// Auto-resize textarea based on content
function setupTextareaAutoResize() {
    const textarea = document.getElementById('message-input');
    
    // Function to adjust height
    function adjustHeight() {
        // Reset height to auto to get the correct scrollHeight
        textarea.style.height = 'auto';
        
        // Calculate new height (clamped between min and max)
        const lineHeight = parseInt(getComputedStyle(textarea).lineHeight);
        const minHeight = 40; // 1 line
        const maxHeight = lineHeight * 5; // 5 lines
        
        // Set new height based on content
        const newHeight = Math.min(Math.max(textarea.scrollHeight, minHeight), maxHeight);
        textarea.style.height = newHeight + 'px';
    }
    
    // Initial adjustment
    adjustHeight();
    
    // Adjust on input
    textarea.addEventListener('input', adjustHeight);
    
    // Reset height when cleared
    textarea.addEventListener('focus', function() {
        if (textarea.value === '') {
            textarea.style.height = '40px';
        }
    });
    
    // Reset height after sending message
    const sendButton = document.getElementById('send-button');
    if (sendButton) {
        sendButton.addEventListener('click', function() {
            setTimeout(function() {
                if (textarea.value === '') {
                    textarea.style.height = '40px';
                }
            }, 10);
        });
    }
}

function scrollToElement(element) {
    if (element) {
        const rect = element.getBoundingClientRect();
        const inputContainer = document.getElementById('input-container');
        const inputRect = inputContainer.getBoundingClientRect();
        
        // Calculate the scroll position needed to make the element visible
        const scrollTop = window.scrollY + rect.top - (inputRect.top - rect.height);
        window.scrollTo({ top: scrollTop, behavior: 'smooth' });
    }
}


// Call this whenever new content is added
function ensureVisible(element) {
    if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }
}


function handleSharedText(text) {
    const input = document.getElementById('message-input');
    if (input) {
        input.value = text;
        // Optionally trigger any related actions
        input.dispatchEvent(new Event('input', { bubbles: true }));
    }
}

// Add this near the message creation logic
function createResendButton(messageId) {
    const resendButton = document.createElement('button');
    resendButton.className = 'message-button resend-button';
    resendButton.title = '重新发送';
    resendButton.innerHTML = "R";
    resendButton.style.display = 'none';
    resendButton.onclick = function(e) {
        e.stopPropagation();
        const messageDiv = document.getElementById('message-' + messageId);
        const content = messageDiv.getAttribute('data-raw-content');
        if (content && chatAndroidReady) {
            // Toggle to interrupt button
            isGenerating = true;
            toggleSendInterruptButtons(true);
            
            // Hide the resend button
            resendButton.style.display = 'none';
            
            // Disable the input while generating
            document.getElementById('message-input').disabled = true;
            
            // Call the Java method to send the message
//            ChatAndroid.sendMessage(content);

            // Call the Java method to send the message
            if (chatAndroidReady) {
                var container = document.getElementById('prompt-message');
                if (container) {
                    var prompt = container.querySelector('.prompt-content').textContent + '\n';
                    container.style.opacity = 0;
                    setTimeout(() => {
                        container.remove();
                    }, 300);
                    ChatAndroid.sendMessage(content, prompt);
                } else {
                    ChatAndroid.sendMessage(content);
                }
            }
        }
    };
    return resendButton;
}


function showMessage(message) {
    // 查找现有提示框
    let container = document.getElementById('prompt-message');

    // 创建提示框元素
    const createMessage = () => {
        container = document.createElement('div');
        container.id = 'prompt-message';
        container.style.position = 'fixed';
        container.style.whiteSpace = 'nowrap'; // 不换行
        container.style.overflowX = 'auto'; // 如果内容超出，显示横向滚动条
        container.style.top = '0';
        container.style.left = '0';
        container.style.width = '95%';
        container.style.maxHeight = '1.5em'; // 最大高度一行字（使用em单位）
        container.style.padding = '12px 12px';
        container.style.backgroundColor = '#333333bf'; // 75% 透明度
        container.style.color = '#fff';
        container.style.boxShadow = '0 2px 5px rgba(0,0,0,0.3)';
        container.style.display = 'flex';
        container.style.alignItems = 'center';
        container.style.justifyContent = 'flex-start'; // 左对齐内容
        container.style.cursor = 'pointer'; // 显示点击手势
        container.style.transition = 'opacity 0.3s';
        container.style.zIndex = '9999';

        const content = document.createElement('div');
        content.className = 'prompt-content';
        content.textContent = message;
        container.appendChild(content);

        // 点击容器关闭
        container.onclick = () => {
            container.style.opacity = 0;
            setTimeout(() => {
                container.remove();
            }, 300);
        };

        document.body.appendChild(container);
    };

    // 如果存在则更新内容并显示
    if (container) {
        const currentContent = container.querySelector('.prompt-content').textContent;
        if (message.toString() === currentContent) {
            container.style.opacity = 0;
            setTimeout(() => {
                container.remove();
            }, 300);
        } else  {
            container.querySelector('.prompt-content').textContent = message;
            container.style.opacity = 1;
            container.style.display = 'flex';
        }
    }
    // 否则创建新提示框
    else {
        createMessage();
    }
}


//-------tools
/**
 * 解析和替换文本中的音频标签
 * @param {string} text - 包含{()}标签的文本
 * @returns {string} 替换后的文本
 */
function processAudioTags(text) {
    // 匹配 {(...)} 模式的正则表达式
    const audioTagRegex = /\{\(([^}]+)\)\}/g;

    return text.replace(audioTagRegex, (match, paramsStr) => {
        try {
            // 解析参数
            const params = parseAudioParams(paramsStr);

            // 生成audio标签
            const audioTag = generateAudioTag(params);

            return audioTag;
        } catch (error) {
            console.warn(`解析音频标签失败: ${match}`, error);
            return match; // 解析失败时返回原文本
        }
    });
}

/**
 * 解析音频标签参数
 * @param {string} paramsStr - 参数字符串
 * @returns {Object} 解析后的参数对象
 */
function parseAudioParams(paramsStr) {
    const parts = paramsStr.split('|').map(part => part.trim());

    // 默认参数
    const params = {
        word: parts[0], // 第一个参数总是单词/短语
        voicename: 'en_us_male', // 默认发音人
        langid: 'eng', // 默认语言
        urlType: 'ou', // 默认URL类型
        tagType: 'audio' // 标签类型
    };

    // 根据参数数量处理不同的格式
    if (parts.length === 2) {
        // 格式: {(word|audio)}
        params.tagType = parts[1];
    } else if (parts.length >= 3) {
        // 格式: {(word|voicename|langid|urlType|tagType)}
        if (parts.length >= 2) params.voicename = parts[1];
        if (parts.length >= 3) params.langid = parts[2];
        if (parts.length >= 4) params.urlType = parts[3];
        if (parts.length >= 5) params.tagType = parts[4];
    }

    return params;
}

/**
 * 生成audio标签
 * @param {Object} params - 参数对象
 * @returns {string} audio标签HTML
 */
function generateAudioTag(params) {
    const { word, voicename, langid, urlType, tagType } = params;

    // 验证参数
    if (!word) {
        throw new Error('缺少单词参数');
    }

    if (tagType !== 'audio') {
        throw new Error(`不支持的标签类型: ${tagType}`);
    }

    // 根据URL类型生成不同的音频URL
    let audioUrl;
    if (urlType === 'ou') {
        // 欧陆URL - 使用之前的TTS API
        audioUrl = getTTSUrl(word, langid, voicename);
    } else if (urlType === 'yd') {
        // 有道URL - 这里需要你提供有道的TTS API格式
        audioUrl = getYoudaoTTSUrl(word, langid, voicename);
    } else {
        throw new Error(`不支持的URL类型: ${urlType}`);
    }

    // 生成audio标签
    return `<audio src="${audioUrl}" controls preload="none" data-word="${word}" data-lang="${langid}" data-voice="${voicename}"></audio>`;
}

/**
 * 获取有道TTS URL（需要根据实际API实现）
 * @param {string} word - 单词
 * @param {string} langid - 语言
 * @param {string} voicename - 发音人
 * @returns {string} 有道TTS URL
 */
function getYoudaoTTSUrl(word, langid, voicename) {
    // 这里需要你提供有道的TTS API实现
    // 暂时返回一个占位URL
    console.warn('有道TTS URL尚未实现，使用占位URL');
    return `https://tts.youdao.com/fanyivoice?word=${encodeURIComponent(word)}&le=${langid}&keyfrom=speaker-target`;
}

/**
 * 获取TTS URL（使用之前实现的函数）
 * @param {string} word - 要转换的文本
 * @param {string} langid - 语言代码
 * @param {string} voicename - 发音人名称
 * @returns {string} TTS API URL
 */
function getTTSUrl(word, langid = 'eng', voicename = 'en_us_female') {
    // 参数验证
    if (!word || typeof word !== 'string') {
        throw new Error('word参数必须是非空字符串');
    }

    // 编码文本：QYN + base64(word) + URL编码
    let base64Encoded;
    if (typeof Buffer !== 'undefined') {
        // Node.js环境
        base64Encoded = Buffer.from(word, 'utf8').toString('base64');
    } else {
        // 浏览器环境
        base64Encoded = btoa(unescape(encodeURIComponent(word)));
    }

    const encodedText = `QYN${base64Encoded}`.replace(/=/g, '%3D');

    // 构建完整的URL
    const baseUrl = 'https://api.frdic.com/api/v2/speech/speakweb';
    const url = `${baseUrl}?langid=${encodeURIComponent(langid)}&voicename=${encodeURIComponent(voicename)}&txt=${encodedText}%0A`;

    return url;
}

/**
 * 批量处理文本中的音频标签
 * @param {string} text - 原始文本
 * @param {Function} callback - 处理完成后的回调函数
 * @returns {Promise<string>} 处理后的文本
 */
function processTextWithAudioTags(text, callback) {
    return new Promise((resolve, reject) => {
        try {
            const processedText = processAudioTags(text);

            if (callback && typeof callback === 'function') {
                callback(null, processedText);
            }

            resolve(processedText);
        } catch (error) {
            if (callback && typeof callback === 'function') {
                callback(error, text);
            }
            reject(error);
        }
    });
}

// 工具函数：获取支持的配置
const AudioTagUtils = {
    /**
     * 获取默认配置
     * @returns {Object} 默认配置
     */
    getDefaultConfig() {
        return {
            defaultVoicename: 'en_us_male',
            defaultLangid: 'eng',
            defaultUrlType: 'ou',
            defaultTagType: 'audio'
        };
    },

    /**
     * 验证参数是否有效
     * @param {Object} params - 参数对象
     * @returns {boolean} 是否有效
     */
    validateParams(params) {
        const required = ['word', 'voicename', 'langid', 'urlType', 'tagType'];
        return required.every(key => params[key]);
    },

    /**
     * 生成简写格式（双参数格式）
     * @param {string} word - 单词
     * @returns {string} 简写格式
     */
    generateShortFormat(word) {
        return `{(${word}|audio)}`;
    },

    /**
     * 生成长格式（完整参数格式）
     * @param {string} word - 单词
     * @param {string} voicename - 发音人
     * @param {string} langid - 语言
     * @param {string} urlType - URL类型
     * @returns {string} 长格式
     */
    generateLongFormat(word, voicename = 'en_us_male', langid = 'eng', urlType = 'ou') {
        return `{(${word}|${voicename}|${langid}|${urlType}|audio)}`;
    }
};

// 测试函数
function testAudioTagProcessing() {
    console.log('=== 音频标签处理测试 ===\n');

    const testCases = [
        // 简写格式
        '这是一个测试：{(hello|audio)}，请听发音。',

        // 完整格式
        '英文单词：{(world|en_uk_male|eng|ou|audio)}',

        // 中文测试
        '中文词语：{(你好|zh_cn_female|chn|ou|audio)}',

        // 混合格式
        `学习单词：
{(apple|audio)} - 苹果
{(banana|en_uk_male|eng|ou|audio)} - 香蕉
{(你好世界|zh_cn_male|chn|ou|audio)} - Hello World`,

        // 复杂文本
        '句子：{(The quick brown fox|en_us_male|eng|ou|audio)} jumps over the lazy dog.'
    ];

    testCases.forEach((testCase, index) => {
        console.log(`测试 ${index + 1}:`);
        console.log(`输入: ${testCase}`);

        try {
            const result = processAudioTags(testCase);
            console.log(`输出: ${result}`);
            console.log('✓ 处理成功\n');
        } catch (error) {
            console.log(`✗ 处理失败: ${error.message}\n`);
        }
    });
}

// 导出函数
if (typeof module !== 'undefined' && module.exports) {
    // Node.js环境
    module.exports = {
        processAudioTags,
        processTextWithAudioTags,
        parseAudioParams,
        generateAudioTag,
        getTTSUrl,
        AudioTagUtils,
        testAudioTagProcessing
    };
} else {
    // 浏览器环境
    window.AudioTagProcessor = {
        processAudioTags,
        processTextWithAudioTags,
        parseAudioParams,
        generateAudioTag,
        getTTSUrl,
        AudioTagUtils,
        testAudioTagProcessing
    };
}

// 运行测试
if (typeof require !== 'undefined' && require.main === module) {
    testAudioTagProcessing();
}

// ========== 滚动进度条功能 ==========
function setupScrollProgressBar() {
    // 延迟确保 DOM 完全渲染
    setTimeout(function() {
        initScrollProgressBar();
    }, 500);
}

function initScrollProgressBar() {
    const chatContainer = document.getElementById('chat-container');
    if (!chatContainer) {
        console.error('chat-container not found');
        return;
    }

    // 检查是否已有进度条，避免重复创建
    if (document.getElementById('scroll-progress-container')) {
        return;
    }

    // 创建进度条容器
    const progressContainer = document.createElement('div');
    progressContainer.className = 'scroll-progress-container';
    progressContainer.id = 'scroll-progress-container';

    // 创建进度条
    const progressBar = document.createElement('div');
    progressBar.className = 'scroll-progress-bar';
    progressBar.id = 'scroll-progress-bar';
    progressContainer.appendChild(progressBar);

    document.body.appendChild(progressContainer);

    console.log('Scroll progress bar created');

    // 监听滚动事件
    chatContainer.addEventListener('scroll', updateScrollProgress);

    // 监听触摸移动事件（移动端优化）
    chatContainer.addEventListener('touchmove', updateScrollProgress);

    // 初始更新
    updateScrollProgress();
}

function updateScrollProgress() {
    requestAnimationFrame(function() {
        const chatContainer = document.getElementById('chat-container');
        const progressBar = document.getElementById('scroll-progress-bar');
        const progressContainer = document.getElementById('scroll-progress-container');

        if (!chatContainer || !progressBar || !progressContainer) return;

        // 如果内容不需要滚动，隐藏进度条
        const scrollHeight = chatContainer.scrollHeight - chatContainer.clientHeight;
        if (scrollHeight <= 0) {
            progressContainer.style.display = 'none';
            return;
        }

        // 显示进度条
        progressContainer.style.display = 'block';

        // 计算滚动进度
        const scrollTop = chatContainer.scrollTop;
        const progress = (scrollTop / scrollHeight) * 100;
        progressBar.style.width = Math.min(progress, 100) + '%';
    });
}

// ========== 消息定位功能 ==========
function setupMessageLocator() {
    // 延迟确保 DOM 完全渲染
    setTimeout(function() {
        initMessageLocator();
    }, 500);
}

function initMessageLocator() {
    // 检查是否启用了定位器
    var showLocator = localStorage.getItem('chat_show_locator');
    if (showLocator === 'false') {
        return; // 用户关闭了定位器，不初始化
    }

    const chatContainer = document.getElementById('chat-container');
    if (!chatContainer) return;

    // 检查是否已有定位器，避免重复创建
    var existingLocator = document.getElementById('message-locator');
    if (existingLocator) {
        // 如果已有，移除后重建（确保状态正确）
        existingLocator.remove();
    }

    // 创建定位器容器
    const locator = document.createElement('div');
    locator.className = 'message-locator';
    locator.id = 'message-locator';
    // 确保正确的样式
    locator.style.cssText = '';

    document.body.appendChild(locator);

    console.log('Message locator created');

    // 初始更新
    updateMessageLocator();
}

// 定位器隐藏定时器
let locatorHideTimer = null;

function updateMessageLocator() {
    // 检查是否启用了定位器
    var showLocator = localStorage.getItem('chat_show_locator');
    if (showLocator === 'false') {
        return; // 用户关闭了定位器，不更新
    }

    const chatContainer = document.getElementById('chat-container');
    let locator = document.getElementById('message-locator');

    // 如果定位器不存在，先检查是否应该创建
    if (!locator && chatContainer) {
        // 再次检查设置，如果关闭了就不创建
        if (localStorage.getItem('chat_show_locator') === 'false') {
            return;
        }
        locator = document.createElement('div');
        locator.className = 'message-locator';
        locator.id = 'message-locator';
        document.body.appendChild(locator);
    }

    if (!chatContainer || !locator) return;

    // 获取所有消息
    const messages = chatContainer.querySelectorAll('.message');

    // 清空定位器
    locator.innerHTML = '';

    if (messages.length <= 1) {
        // 只有一条或更少消息时隐藏定位器
        locator.classList.remove('visible');
        return;
    }

    // 如果 currentMessageIndex 为 -1，初始化为最后一条消息的索引
    if (currentMessageIndex === -1) {
        currentMessageIndex = messages.length - 1;
    }

    // 为每条消息创建定位点 - 均匀分布
    messages.forEach((message, index) => {
        const dot = document.createElement('button');
        dot.className = 'locator-dot';

        // 根据消息类型添加类名
        if (message.classList.contains('user-message')) {
            dot.classList.add('user');
        } else {
            dot.classList.add('assistant');
        }

        // 如果是当前显示的消息，添加active类
        if (index === currentMessageIndex) {
            dot.classList.add('active');
        }

        // 点击定位到对应消息
        dot.onclick = function(e) {
            e.stopPropagation();
            scrollToMessage(index);
        };

        locator.appendChild(dot);
    });

    // 确保定位器可见
    locator.classList.add('visible');

    // 清除之前的定时器
    if (locatorHideTimer) {
        clearTimeout(locatorHideTimer);
    }

    // 3秒后隐藏
    locatorHideTimer = setTimeout(function() {
        if (locator) {
            locator.classList.remove('visible');
        }
    }, 3000);
}

// 显示定位器（滚动时调用）
// rebuild: 是否重建定位器，true=重建并更新高亮，false=只显示不重建
function showLocator(rebuild = true) {
    // 检查是否启用了定位器
    var showLocator = localStorage.getItem('chat_show_locator');
    if (showLocator === 'false') {
        return; // 用户关闭了定位器，不显示
    }

    const chatContainer = document.getElementById('chat-container');
    let locator = document.getElementById('message-locator');

    if (!chatContainer) return;

    // 获取消息数量
    const messages = chatContainer.querySelectorAll('.message');
    if (messages.length <= 1) return;

    // 如果定位器不存在，创建它
    if (!locator) {
        // 再次检查设置
        if (localStorage.getItem('chat_show_locator') === 'false') {
            return;
        }
        locator = document.createElement('div');
        locator.className = 'message-locator';
        locator.id = 'message-locator';
        document.body.appendChild(locator);
    }

    // 如果需要重建
    if (rebuild) {
        // 重建定位点 - 均匀分布
        locator.innerHTML = '';

        // 如果 currentMessageIndex 为 -1，初始化为最后一条消息的索引
        if (currentMessageIndex === -1) {
            currentMessageIndex = messages.length - 1;
        }

        // 直接使用 currentMessageIndex，不重新计算
        messages.forEach((message, index) => {
        const dot = document.createElement('button');
        dot.className = 'locator-dot';

        // 用户消息用绿色
        if (message.classList.contains('user-message')) {
            dot.classList.add('user');
        } else {
            dot.classList.add('assistant');
        }

        // 当前消息高亮
        if (index === currentMessageIndex) {
            dot.classList.add('active');
        }

        dot.onclick = function(e) {
            e.stopPropagation();
            scrollToMessage(index);
        };

        locator.appendChild(dot);
        });

        // 显示定位器
        locator.classList.add('visible');
    } else {
        // 只显示，不重建
        locator.classList.add('visible');
    }

    // 清除之前的定时器
    if (locatorHideTimer) {
        clearTimeout(locatorHideTimer);
    }

    // 3秒后隐藏
    locatorHideTimer = setTimeout(function() {
        if (locator) {
            locator.classList.remove('visible');
        }
    }, 3000);
}

// 隐藏定位器
function hideLocator() {
    const locator = document.getElementById('message-locator');
    if (locator) {
        locator.classList.remove('visible');
    }

    if (locatorHideTimer) {
        clearTimeout(locatorHideTimer);
        locatorHideTimer = null;
    }
}

function scrollToMessage(index) {
    const chatContainer = document.getElementById('chat-container');
    const messages = chatContainer.querySelectorAll('.message');
    const locator = document.getElementById('message-locator');

    if (index >= 0 && index < messages.length) {
        const targetMessage = messages[index];
        currentMessageIndex = index;

        // 如果定位器不存在，创建它
        if (!locator) {
            const newLocator = document.createElement('div');
            newLocator.className = 'message-locator';
            newLocator.id = 'message-locator';
            document.body.appendChild(newLocator);
            // 需要初始化所有圆点...
            initLocatorDots(newLocator, messages);
        }

        // 只更新 active 类，不重建整个定位器
        const dots = locator.querySelectorAll('.locator-dot');
        dots.forEach((dot, i) => {
            if (i === index) {
                dot.classList.add('active');
            } else {
                dot.classList.remove('active');
            }
        });

        locator.classList.add('visible');

        // 滚动到目标消息
        targetMessage.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

// 辅助函数：初始化定位器圆点（只在首次或消息数量变化时调用）
function initLocatorDots(locator, messages) {
    locator.innerHTML = '';
    messages.forEach((message, i) => {
        const dot = document.createElement('button');
        dot.className = 'locator-dot';
        if (message.classList.contains('user-message')) {
            dot.classList.add('user');
        } else {
            dot.classList.add('assistant');
        }
        dot.onclick = function(e) {
            e.stopPropagation();
            scrollToMessage(i);
        };
        locator.appendChild(dot);
    });
}

function updateLocatorHighlight(activeIndex) {
    const locator = document.getElementById('message-locator');
    if (!locator) return;

    const dots = locator.querySelectorAll('.locator-dot');
    dots.forEach((dot, index) => {
        if (index === activeIndex) {
            dot.classList.add('active');
        } else {
            dot.classList.remove('active');
        }
    });
}

// 注册消息滚动监听
document.addEventListener('DOMContentLoaded', function() {
    // 只初始化定位器组件
    const chatContainer = document.getElementById('chat-container');

    // 初始化滚动进度条（如果尚未初始化）
    if (!document.getElementById('scroll-progress-container')) {
        initScrollProgressBar();
    }

    // 初始化消息定位器（如果尚未初始化）
    if (!document.getElementById('message-locator')) {
        initMessageLocator();
    }

    console.log('Components initialized');
});

// 简化版：计算当前可见消息索引
function updateCurrentMessageIndex() {
    const chatContainer = document.getElementById('chat-container');
    const messages = chatContainer.querySelectorAll('.message');

    if (messages.length === 0) return;

    const scrollTop = chatContainer.scrollTop;
    const containerHeight = chatContainer.clientHeight;
    const viewportCenter = scrollTop + containerHeight / 2;

    let minDistance = Infinity;
    let bestIndex = 0;

    messages.forEach((message, index) => {
        const msgTop = message.offsetTop;
        const msgBottom = msgTop + message.offsetHeight;
        const msgCenter = (msgTop + msgBottom) / 2;
        const distance = Math.abs(msgCenter - viewportCenter);

        if (distance < minDistance) {
            minDistance = distance;
            bestIndex = index;
        }
    });

    currentMessageIndex = bestIndex;
}

// ========== 磨砂状态栏高度检测 ==========
// 记录首次检测到的真实状态栏高度（不受键盘影响）
var _baselineStatusBarHeight = 0;

// 可被 Android 端通过 evaluateJavascript 或 JavascriptInterface 调用
function updateStatusBarHeight(px) {
    var height = 0;
    if (typeof px === 'number' && px > 0) {
        height = px;
    } else if (window.visualViewport && window.visualViewport.offsetTop > 0) {
        height = window.visualViewport.offsetTop;
    } else {
        // fallback: 尝试通过 safe-area-inset-top 获取
        var dummy = document.createElement('div');
        dummy.style.cssText = 'position:fixed;top:0;left:0;width:1px;height:1px;padding-top:env(safe-area-inset-top, 0px);pointer-events:none;opacity:0';
        document.body.appendChild(dummy);
        height = parseInt(getComputedStyle(dummy).paddingTop) || 0;
        dummy.remove();
    }
    height = Math.max(height, 0);
    // 记录首次基线高度
    if (_baselineStatusBarHeight === 0 && height > 0) {
        _baselineStatusBarHeight = height;
    }
    document.documentElement.style.setProperty('--status-bar-height', height + 'px');
}

// 监听 visualViewport 变化（包括键盘弹起/收起和方向旋转）
if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', function() {
        // 键盘弹出时 visualViewport.offsetTop 可能变化，
        // 用基线高度确保键盘收回后状态栏高度正确恢复
        var h = _baselineStatusBarHeight;
        if (h === 0) {
            // 基线还没建立，直接取当前值
            updateStatusBarHeight();
        } else {
            document.documentElement.style.setProperty('--status-bar-height', h + 'px');
        }
    });
}

// 初始化入口 - 页面加载完成后延迟初始化
window.addEventListener('load', function() {
    console.log('Window load event fired');

    // 检测状态栏高度并设置 CSS 变量
    updateStatusBarHeight();

    // 多次尝试初始化，确保 WebView 和 DOM 完全加载
    let initAttempts = 0;
    const maxAttempts = 10;

    function tryInit() {
        initAttempts++;
        console.log('Init attempt:', initAttempts);

        const chatContainer = document.getElementById('chat-container');
        if (!chatContainer) {
            if (initAttempts < maxAttempts) {
                setTimeout(tryInit, 200);
            } else {
                console.error('Failed to find chat-container after', maxAttempts, 'attempts');
            }
            return;
        }

        console.log('Found chat-container, initializing components');

        // 初始化滚动进度条（如果尚未初始化）
        if (!document.getElementById('scroll-progress-container')) {
            initScrollProgressBar();
        }

        // 初始化消息定位器（如果尚未初始化且设置为显示）
        var showLocator = localStorage.getItem('chat_show_locator');
        if (!document.getElementById('message-locator') && showLocator !== 'false') {
            initMessageLocator();
        }

        // 应用回弹设置
        applyBounceSetting(localStorage.getItem('chat_allow_bounce') !== 'false');

        console.log('Components initialized');
    }

    // 首次尝试
    setTimeout(tryInit, 100);
});

// 回弹效果控制
function applyBounceSetting(allow) {
    var container = document.getElementById('chat-container');
    if (!container) return;
    if (allow) {
        container.style.overscrollBehavior = 'auto';
    } else {
        container.style.overscrollBehavior = 'contain';
    }
}

// 磨砂模式控制：true=实时刷新，false=滚动停止后刷新
var _frostScrollTimer = null;

// 需要控制 backdrop-filter 的元素列表
function _getFrostElements() {
    var els = [];
    var container = document.getElementById('input-container');
    if (container) els.push(container);
    var fab = document.getElementById('menu-fab');
    if (fab) els.push(fab);
    var panel = document.getElementById('menu-panel');
    if (panel) els.push(panel);
    return els;
}

function _setFrostFilter(els, val) {
    for (var i = 0; i < els.length; i++) {
        els[i].style.backdropFilter = val;
        els[i].style.webkitBackdropFilter = val;
    }
}

function _setFrostTransition(els, val) {
    for (var i = 0; i < els.length; i++) {
        els[i].style.transition = val;
    }
}

function _getInputBgColor() {
    var isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    if (isDark) {
        // 暗色主题使用默认暗色背景，忽略用户颜色选择
        return 'rgba(30, 30, 30, 0.96)';
    }
    var hex = localStorage.getItem('chat_input_bg_color') || '#15ae67';
    var r = parseInt(hex.substring(1,3), 16);
    var g = parseInt(hex.substring(3,5), 16);
    var b = parseInt(hex.substring(5,7), 16);
    // 0.96 透明度对应非实时模式的静态背景
    return 'rgba(' + r + ',' + g + ',' + b + ',0.96)';
}

function applyFrostMode(realtime) {
    var els = _getFrostElements();
    if (els.length === 0) return;
    var container = els[0];
    if (realtime) {
        // 实时：恢复 backdrop-filter，用 CSS 定义的磨砂背景
        _setFrostFilter(els, '');
        _setFrostTransition(els, '');
        container.style.background = '';
    } else {
        // 非实时：去掉 backdrop-filter，背景用不透明色 + 噪点纹理
        _setFrostFilter(els, 'none');
        _setFrostTransition(els, '');
        // 把背景设成不透明，::before 噪点层保持不变即可
        container.style.background = _getInputBgColor();
    }
}

// 非实时模式：滚动时保持静态效果，不需要额外操作
function onChatScrollForFrost() {
    // 什么都不做——非实时模式下 backdrop-filter 已经是 none，
    // 背景色 + ::before 噪点纹理由 CSS 管理，本身就不动
}

// 设置字体大小
function setFontSize(fontSize) {
    localStorage.setItem('chat_font_size', fontSize);
    document.documentElement.style.setProperty('--font-size', fontSize + 'px');
}

// 页面加载时应用保存的字体大小
(function() {
    var saved = localStorage.getItem('chat_font_size');
    if (saved) {
        document.documentElement.style.setProperty('--font-size', saved + 'px');
    }
})();

// 预设颜色列表
var presetColors = ['#ffffff','#15ae67','#006994','#5387ED','#9C27B0','#E91E63','#FF5722','#FF9800','#795548','#607D8B','#4CAF50','#2196F3','#3F51B5','#673AB7','#F44336','#FFEB3B','#009688'];

// 创建预设色板
function createColorPresets(inputEl_or_null, onSelect, selectedColor) {
    var container = document.createElement('div');
    container.className = 'page-settings-color-presets';
    var sel = selectedColor || (inputEl_or_null && inputEl_or_null.value) || '#15ae67';
    presetColors.forEach(function(c) {
        var swatch = document.createElement('div');
        swatch.className = 'page-settings-color-swatch';
        swatch.style.background = c;
        if (c === sel) {
            swatch.classList.add('selected');
        }
        swatch.onclick = function() {
            container.querySelectorAll('.page-settings-color-swatch').forEach(function(s) { s.classList.remove('selected'); });
            swatch.classList.add('selected');
            if (onSelect) onSelect(c);
        };
        container.appendChild(swatch);
    });
    // 自定义颜色块
    var customSwatch = document.createElement('div');
    customSwatch.className = 'page-settings-color-swatch page-settings-color-custom';
    customSwatch.textContent = '+';
    customSwatch.onclick = function() {
        var input = document.createElement('input');
        input.type = 'color';
        input.value = sel;
        input.style.cssText = 'position:fixed;top:0;left:-9999px;width:1px;height:1px;opacity:0;pointer-events:none';
        document.body.appendChild(input);
        input.addEventListener('input', function() {
            var hex = this.value;
            container.querySelectorAll('.page-settings-color-swatch').forEach(function(s) { s.classList.remove('selected'); });
            customSwatch.style.background = hex;
            customSwatch.classList.add('selected');
            customSwatch.style.color = '#fff';
            customSwatch.textContent = '';
            if (onSelect) onSelect(hex);
        });
        input.addEventListener('blur', function() {
            this.remove();
        });
        // 延迟触发点击以确保 input 在 DOM 中
        setTimeout(function() { input.click(); }, 10);
    };
    container.appendChild(customSwatch);
    return container;
}

// 将 hex 颜色应用到 input-container
function applyInputBgColor(hex) {
    var r = parseInt(hex.substring(1,3), 16);
    var g = parseInt(hex.substring(3,5), 16);
    var b = parseInt(hex.substring(5,7), 16);
    document.getElementById('input-container').style.background = 'rgba(' + r + ',' + g + ',' + b + ',0.18)';
}

// 将 hex 颜色应用到进度条和定位器
function applyProgressColor(hex) {
    var r = parseInt(hex.substring(1,3), 16);
    var g = parseInt(hex.substring(3,5), 16);
    var b = parseInt(hex.substring(5,7), 16);
    document.getElementById('scroll-progress-bar').style.background = 'linear-gradient(90deg, rgba(' + r + ',' + g + ',' + b + ',0.6), rgb(' + r + ',' + g + ',' + b + '))';
    // 同步更新定位器颜色
    document.getElementById('progress-color-style') || (function() {
        var style = document.createElement('style');
        style.id = 'progress-color-style';
        document.head.appendChild(style);
    })();
    // 反色：255 - 原色值
    var ri = 255 - r, gi = 255 - g, bi = 255 - b;
    document.getElementById('progress-color-style').textContent =
        '.locator-dot:not(.locator-bottom) { background: rgba(' + r + ',' + g + ',' + b + ',0.35) !important; }' +
        '.locator-dot:not(.locator-bottom):hover { background: rgba(' + r + ',' + g + ',' + b + ',0.7) !important; }' +
        '.locator-dot.user { background: rgba(' + ri + ',' + gi + ',' + bi + ',0.5) !important; }' +
        '.locator-dot.assistant { background: rgba(' + r + ',' + g + ',' + b + ',0.35) !important; }' +
        '.locator-dot.active:not(.locator-bottom) { transform: scale(1.6); }';
}

// 初始化设置按钮
function initPageSettingsButton() {
    // 页面字体初始化
    var saved = localStorage.getItem('chat_font_size');
    if (saved) {
        document.documentElement.style.setProperty('--font-size', saved + 'px');
    }
    // 恢复输入框背景颜色
    var savedColor = localStorage.getItem('chat_input_bg_color');
    if (savedColor) {
        applyInputBgColor(savedColor);
    }
    // 恢复进度条颜色
    var savedProgress = localStorage.getItem('chat_progress_color');
    if (savedProgress) {
        applyProgressColor(savedProgress);
    }
    // 恢复磨砂模式
    var frostRealtime = localStorage.getItem('chat_frost_realtime') !== 'false';
    applyFrostMode(frostRealtime);

    // 恢复 prompt 显隐状态
    var promptsHidden = localStorage.getItem('chat_prompts_hidden') === 'true';
    var pc = document.getElementById('prompt-container');
    var toggleBtn = document.getElementById('toggle-prompts-btn');
    if (promptsHidden && pc && toggleBtn) {
        pc.style.display = 'none';
        toggleBtn.style.opacity = '0.4';
        var msgInput = document.getElementById('message-input');
        if (msgInput) {
            msgInput.style.marginTop = '8px';
        }
    }
}

// 设置对话框函数
function showPageSettingsDialog() {
    try {
        var fontSize = localStorage.getItem('chat_font_size') || 16;
        var showLocator = localStorage.getItem('chat_show_locator') !== 'false';  // 默认显示

        var overlay = document.createElement('div');
        overlay.className = 'page-settings-overlay';
        overlay.onclick = function(e) {
            if (e.target === overlay) {
                overlay.remove();
            }
        };

        var dialog = document.createElement('div');
        dialog.className = 'page-settings';
        dialog.onclick = function(e) {
            e.stopPropagation();
        };

        var title = document.createElement('h3');
        title.className = 'page-settings-title';
        title.textContent = '设置';

        // 字体大小设置
        var label = document.createElement('div');
        label.className = 'page-settings-label';
        label.textContent = '字体大小: ' + fontSize + 'sp';

        var slider = document.createElement('input');
        slider.type = 'range';
        slider.min = '12';
        slider.max = '24';
        slider.value = fontSize;
        slider.step = '2';
        slider.className = 'page-settings-slider';

        slider.oninput = function() {
            var size = parseInt(this.value);
            label.textContent = '字体大小: ' + size + 'sp';
            // 实时修改字体
            document.documentElement.style.setProperty('--font-size', size + 'px');
        };

        // 定位器复选框
        var locatorLabel = document.createElement('label');
        locatorLabel.className = 'page-settings-checkbox-label';

        var locatorCheckbox = document.createElement('input');
        locatorCheckbox.type = 'checkbox';
        locatorCheckbox.checked = showLocator;
        locatorCheckbox.className = 'page-settings-checkbox';

        var locatorText = document.createElement('span');
        locatorText.textContent = '启动定位器';

        locatorLabel.appendChild(locatorCheckbox);
        locatorLabel.appendChild(locatorText);

        // 回弹效果复选框
        var bounceLabel = document.createElement('label');
        bounceLabel.className = 'page-settings-checkbox-label';

        var bounceCheckbox = document.createElement('input');
        bounceCheckbox.type = 'checkbox';
        bounceCheckbox.checked = localStorage.getItem('chat_allow_bounce') !== 'false';
        bounceCheckbox.className = 'page-settings-checkbox';

        var bounceText = document.createElement('span');
        bounceText.textContent = '页面回弹';

        bounceLabel.appendChild(bounceCheckbox);
        bounceLabel.appendChild(bounceText);

        // 实时磨砂复选框
        var frostLabel = document.createElement('label');
        frostLabel.className = 'page-settings-checkbox-label';

        var frostCheckbox = document.createElement('input');
        frostCheckbox.type = 'checkbox';
        frostCheckbox.checked = localStorage.getItem('chat_frost_realtime') !== 'false';
        frostCheckbox.className = 'page-settings-checkbox';

        var frostText = document.createElement('span');
        frostText.textContent = '实时磨砂';

        frostLabel.appendChild(frostCheckbox);
        frostLabel.appendChild(frostText);

        // 输入框背景颜色选择
        var colorRow = document.createElement('div');
        colorRow.className = 'page-settings-color-row';

        var colorLabel = document.createElement('label');
        colorLabel.textContent = '输入框背景';
        colorLabel.htmlFor = 'input-bg-color';

        var savedColor = localStorage.getItem('chat_input_bg_color') || '#15ae67';

        colorRow.appendChild(colorLabel);
        colorRow.appendChild(createColorPresets(null, function(hex) {
            applyInputBgColor(hex);
            localStorage.setItem('chat_input_bg_color', hex);
        }, savedColor));

        // 进度条颜色选择
        var progressRow = document.createElement('div');
        progressRow.className = 'page-settings-color-row';

        var progressLabel = document.createElement('label');
        progressLabel.textContent = '进度条颜色';

        var savedProgress = localStorage.getItem('chat_progress_color') || '#006994';

        progressRow.appendChild(progressLabel);
        progressRow.appendChild(createColorPresets(null, function(hex) {
            applyProgressColor(hex);
            localStorage.setItem('chat_progress_color', hex);
        }, savedProgress));

        var btnContainer = document.createElement('div');
        btnContainer.className = 'page-settings-buttons';

        var cancelBtn = document.createElement('button');
        cancelBtn.className = 'page-settings-btn';
        cancelBtn.textContent = '取消';
        cancelBtn.onclick = function() {
            localStorage.setItem('chat_font_size', fontSize);
            document.documentElement.style.setProperty('--font-size', fontSize + 'px');
            // 恢复回弹设置
            var origBounce = localStorage.getItem('chat_allow_bounce') !== 'false';
            applyBounceSetting(origBounce);
            overlay.remove();
        };

        var okBtn = document.createElement('button');
        okBtn.className = 'page-settings-btn page-settings-btn-primary';
        okBtn.textContent = '确定';
        okBtn.onclick = function() {
            var size = parseInt(slider.value);
            localStorage.setItem('chat_font_size', size);
            document.documentElement.style.setProperty('--font-size', size + 'px');

            // 保存定位器设置
            var showLocator = locatorCheckbox.checked;
            localStorage.setItem('chat_show_locator', showLocator);

            // 保存回弹设置
            var allowBounce = bounceCheckbox.checked;
            localStorage.setItem('chat_allow_bounce', allowBounce);
            applyBounceSetting(allowBounce);

            // 保存实时磨砂设置
            var realtime = frostCheckbox.checked;
            localStorage.setItem('chat_frost_realtime', realtime);
            applyFrostMode(realtime);

            // 根据设置显示或隐藏定位器
            var locator = document.getElementById('message-locator');
            if (showLocator) {
                if (!locator) {
                    // 定位器不存在，创建新的
                    initMessageLocator();
                } else {
                    // 定位器存在，先移除再重建，避免样式问题
                    locator.remove();
                    initMessageLocator();
                }
            } else {
                if (locator) {
                    locator.remove();
                }
            }

            overlay.remove();
        };

        btnContainer.appendChild(cancelBtn);
        btnContainer.appendChild(okBtn);
        dialog.appendChild(title);
        dialog.appendChild(label);
        dialog.appendChild(slider);
        dialog.appendChild(locatorLabel);
        dialog.appendChild(bounceLabel);
        dialog.appendChild(frostLabel);
        dialog.appendChild(colorRow);
        dialog.appendChild(progressRow);
        dialog.appendChild(btnContainer);
        overlay.appendChild(dialog);
        document.body.appendChild(overlay);
    } catch (err) {
        alert('弹窗出错: ' + err.message);
    }
}

// 显示本地设置对话框（当 ChatAndroid 不可用时）
function showLocalPageSettingsDialog() {
    // 获取当前字体大小设置
    let currentFontSize = localStorage.getItem('chat_font_size') || 16;

    // 创建对话框overlay
    const overlay = document.createElement('div');
    overlay.id = 'page-settings-overlay';
    overlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 20000;
    `;

    // 创建对话框容器
    const dialog = document.createElement('div');
    dialog.style.cssText = `
        background: var(--color-bg, #ffffff);
        border-radius: 12px;
        padding: 20px;
        width: 80%;
        max-width: 300px;
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
    `;

    // 标题
    const title = document.createElement('h3');
    title.textContent = '设置';
    title.style.cssText = `
        margin: 0 0 16px 0;
        text-align: center;
        color: var(--color-text, #24292e);
    `;

    // 字体大小标签
    const fontSizeLabel = document.createElement('div');
    fontSizeLabel.id = 'font-size-label';
    fontSizeLabel.textContent = '字体大小: ' + currentFontSize + 'sp';
    fontSizeLabel.style.cssText = `
        margin-bottom: 8px;
        color: var(--color-text, #24292e);
    `;

    // 字体大小滑块
    const fontSizeSlider = document.createElement('input');
    fontSizeSlider.type = 'range';
    fontSizeSlider.min = '12';
    fontSizeSlider.max = '24';
    fontSizeSlider.value = currentFontSize;
    fontSizeSlider.step = '2';
    fontSizeSlider.style.cssText = `
        width: 100%;
        margin-bottom: 16px;
    `;

    // 滑块变化时更新标签
    fontSizeSlider.addEventListener('input', function() {
        fontSizeLabel.textContent = '字体大小: ' + this.value + 'sp';
    });

    // 按钮容器
    const buttonContainer = document.createElement('div');
    buttonContainer.style.cssText = `
        display: flex;
        justify-content: flex-end;
        gap: 12px;
    `;

    // 取消按钮
    const cancelBtn = document.createElement('button');
    cancelBtn.textContent = '取消';
    cancelBtn.style.cssText = `
        padding: 8px 16px;
        border: none;
        border-radius: 6px;
        background: #e0e0e0;
        cursor: pointer;
    `;
    cancelBtn.addEventListener('click', function() {
        overlay.remove();
    });

    // 确定按钮
    const confirmBtn = document.createElement('button');
    confirmBtn.textContent = '确定';
    confirmBtn.style.cssText = `
        padding: 8px 16px;
        border: none;
        border-radius: 6px;
        background: #5387ED;
        color: white;
        cursor: pointer;
    `;
    confirmBtn.addEventListener('click', function() {
        var fontSize = parseInt(fontSizeSlider.value);
        localStorage.setItem('chat_font_size', fontSize);
        applyFontSize(fontSize);
        showToast('字体已设置为 ' + fontSize + 'sp');
        overlay.remove();
    });

    // 组装对话框
    buttonContainer.appendChild(cancelBtn);
    buttonContainer.appendChild(confirmBtn);
    dialog.appendChild(title);
    dialog.appendChild(fontSizeLabel);
    dialog.appendChild(fontSizeSlider);
    dialog.appendChild(buttonContainer);
    overlay.appendChild(dialog);
    document.body.appendChild(overlay);

    // 点击背景关闭
    overlay.addEventListener('click', function(e) {
        if (e.target === overlay) {
            overlay.remove();
        }
    });
}

// 页面加载后应用字体设置
document.addEventListener('DOMContentLoaded', function() {
    initPageSettingsButton();
});
