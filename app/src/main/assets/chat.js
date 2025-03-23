// 全局变量
let chatAndroidReady = false;
let isSingleTurnMode = false;
let isGenerating = false;

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
    
    // Setup interrupt button (only call once)
    setupInterruptButton();
    
    // 发送消息处理
    function sendMessage() {
        const messageInput = document.getElementById('message-input');
        var content = messageInput.value.trim();

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
        }
    }
    
    // 绑定事件监听器
    sendButton.addEventListener('click', sendMessage);
    // messageInput.addEventListener('keypress', function(e) {
    //     if (e.key === 'Enter') {
    //         sendMessage();
    //     }
    // });
    
    // 初始检查
    checkChatAndroid();

    // 获取模态框元素
    const sessionsModal = document.getElementById('sessions-modal');
    
    // 点击模态框背景时关闭
    sessionsModal.addEventListener('click', function(e) {
        // 如果点击的是模态框本身（而不是内容区域）
        if (e.target === sessionsModal) {
            sessionsModal.style.display = 'none';
            document.getElementById('show-sessions-btn').textContent = '历史会话';
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
    messageBubble.style.width = '88%';
    
    // 消息内容
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content markdown-body';
    contentDiv.innerHTML = formatMessage(message.content || '');
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
        
        // 添加按钮到按钮容器
        buttonsDiv.appendChild(saveButton);
        buttonsDiv.appendChild(saveSourceButton);
        buttonsDiv.appendChild(toggleButton);
        
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
    
    console.log('Created new message element:', messageId);
    scrollToBottom();

    // 添加触摸事件监听
    messageDiv.addEventListener('click', function(e) {
        // 如果点击的是按钮，不处理
        if (e.target.closest('.message-buttons')) {
            return;
        }
        
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
        
        // Save math expressions
        const mathExpressions = [];
        let processedContent = content.replace(/(\$\$[\s\S]*?\$\$|\$[\s\S]*?\$|\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\))/g, (match, p1, offset) => {
            mathExpressions.push(p1);
            return `@@MATH_EXPR_${mathExpressions.length - 1}@@`;
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
        processedContent = processedContent.replace(/@@MATH_EXPR_(\d+)@@/g, (match, index) => {
            return mathExpressions[parseInt(index)];
        });

        return processedContent;
    } catch (e) {
        console.error('Error formatting message:', e);
        return content;
    }
}

// 滚动到底部
function scrollToBottom() {
    const chatContainer = document.getElementById('chat-container');
    if (chatContainer) {
        chatContainer.scrollTop = chatContainer.scrollHeight;
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
    if (confirm('确定要开始新会话吗？当前会话将被保存。')) {
        ChatAndroid.newSession();
    }
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
        this.textContent = '关闭历史';
    }
};

// 修改关闭按钮的点击事件
document.querySelector('.modal-close').onclick = function() {
    document.getElementById('sessions-modal').style.display = 'none';
    document.getElementById('show-sessions-btn').textContent = '历史会话';
};

// 加载会话
function loadSession(sessionId) {
    const modal = document.getElementById('sessions-modal');
    const showSessionsBtn = document.getElementById('show-sessions-btn');

    // 隐藏模态框
    modal.style.display = 'none';
    // 更新按钮文本
    showSessionsBtn.textContent = '历史会话';

    ChatAndroid.loadSession(sessionId);

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
        buttonsDiv.style.left = (rect.right + 8) + 'px';
    }
    
    // 计算按钮高度和消息高度
    const buttonHeight = buttonsDiv.offsetHeight;
    const messageHeight = rect.height;
    
    // 计算垂直居中位置
    let top = rect.top + (messageHeight - buttonHeight) / 2;
    
    // 确保按钮不会超出视窗顶部和输入框
    const maxTop = inputRect.top - buttonHeight - 8;
    const minTop = 8;
    
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
    if (messageDiv && buttonsDiv.style.opacity == '0') {
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

// 添加滚动处理
let isScrolling;
document.getElementById('chat-container').addEventListener('scroll', function() {
    // 隐藏所有按钮
    document.querySelectorAll('.message-buttons').forEach(buttons => {
        buttons.style.opacity = '0';
        buttons.style.pointerEvents = 'none';
    });

    // 清除之前的定时器
    clearTimeout(isScrolling);

    // 设置新的定时器，滚动停止后恢复最后点击的消息的按钮
    isScrolling = setTimeout(() => {
        const lastClickedMessage = document.querySelector('.message.last-clicked');
        if (lastClickedMessage) {
            updateButtonsVisibility(lastClickedMessage);
        }
    }, 100); // 100ms 后恢复
}); 

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
            
            // 处理代码高亮
            contentDiv.querySelectorAll('pre code').forEach((block) => {
                hljs.highlightElement(block);
            });
            
            // 滚动到底部
            scrollToBottom();
        }
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
    modeIndicator.textContent = isSingleTurnMode ? '单轮对话' : '多轮对话';
    
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
            console.log("Interrupt button clicked, isGenerating:", isGenerating);
            if (isGenerating) {
                console.log("Calling ChatAndroid.interruptResponse()");
                ChatAndroid.interruptResponse();
                toggleSendInterruptButtons(false);
                isGenerating = false;
                
                // Re-enable the input
                document.getElementById('message-input').disabled = false;
            }
        });
    } else {
        console.error("Interrupt button not found in the DOM");
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

// Add event listener for focus events
document.addEventListener('focus', function(e) {
    if (e.target.tagName === 'TEXTAREA' || e.target.tagName === 'INPUT') {
        setTimeout(() => {
            scrollToElement(e.target);
        }, 300); // Delay to allow keyboard to fully appear
    }
}, true);

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
            ChatAndroid.sendMessage(content);
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
        container.style.top = '0';
        container.style.left = '0';
        container.style.width = '90%';
        container.style.padding = '12px 24px';
        container.style.backgroundColor = '#333';
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
