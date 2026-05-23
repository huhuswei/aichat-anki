// Prompt management functions

document.addEventListener('DOMContentLoaded', function() {
    loadPrompts();
});

window.loadPrompts = function() {
    console.log('loadPrompts called');
    try {
        var json = ChatAndroid ? ChatAndroid.loadPrompts() : '[]';
        var arr = JSON.parse(json || '[]');
        renderPrompts(arr);
    } catch(e) {
        console.error('ERR:', e);
    }
};

function renderPrompts(arr) {
    var container = document.getElementById('prompt-container');
    if (!container) return;
    container.innerHTML = '';

    if (!arr || !arr.length) return;

    var list = document.createElement('div');
    list.className = 'prompts-list';

    for (var i = 0; i < arr.length; i++) {
        (function(content, title) {
            var chip = document.createElement('span');
            chip.className = 'prompt-chip';
            chip.textContent = title;
            chip.onclick = function() {
                // 调用 Android 把内容填入输入框
                ChatAndroid.appendPrompt(content);
            };
            list.appendChild(chip);
        })(arr[i].content, arr[i].title);
    }

    container.appendChild(list);
}

window.refreshPrompts = window.loadPrompts;
