// ===== Config Panel =====
(function() {
    var configContainer = document.getElementById('config-container');
    var serverSelect = document.getElementById('config-server');
    var modelSelect = document.getElementById('config-model');
    var formatSelect = document.getElementById('config-format');
    var deckSelect = document.getElementById('config-deck');

    var serverConfigs = [];

    // ===== Native Bridge =====
    function safeCall(fn, fallback) {
        try { return fn(); } catch(e) { console.warn('Config bridge:', e); return fallback; }
    }

    var NATIVE = {
        getServerConfigs: function() {
            return safeCall(function() { return MainAndroid ? MainAndroid.getServerConfigs() : '[]'; }, '[]');
        },
        getAnkiDecks: function() {
            return safeCall(function() {
                return (typeof ChatAndroid !== 'undefined') ? ChatAndroid.getAnkiDecks() : '[]';
            }, '[]');
        },
        getCurrentServerId: function() {
            return safeCall(function() { return MainAndroid ? MainAndroid.getCurrentServerId() : ''; }, '');
        },
        selectServer: function(id) {
            safeCall(function() { if (MainAndroid) MainAndroid.selectServer(id); });
        },
        reinitChatService: function() {
            safeCall(function() { if (MainAndroid) MainAndroid.reinitChatService(); });
        },
        selectModel: function(model) {
            safeCall(function() { if (MainAndroid) MainAndroid.selectModel(model); });
        },
        selectFormat: function(format) {
            safeCall(function() { if (MainAndroid) MainAndroid.selectFormat(format); });
        },
        getCurrentDeckId: function() {
            return safeCall(function() { return MainAndroid ? MainAndroid.getCurrentDeckId() : ''; }, '');
        },
        selectDeck: function(id) {
            safeCall(function() { if (MainAndroid) MainAndroid.selectDeck(Number(id)); });
        }
    };

    // ===== Public API =====

    window.loadConfigPanel = function() {
        serverConfigs = JSON.parse(NATIVE.getServerConfigs() || '[]');
        renderServerSelect();

        var decks = JSON.parse(NATIVE.getAnkiDecks() || '[]');
        renderDeckSelect(decks);
    };

    window.toggleConfigPanel = function(show) {
        var visible;
        if (typeof show === 'boolean') {
            visible = show;
        } else {
            visible = !configContainer.classList.contains('visible');
        }
        configContainer.classList.toggle('visible', visible);
        if (visible) {
            window.loadConfigPanel();
        }
    };

    window.getConfigPanelVisible = function() {
        return configContainer.classList.contains('visible');
    };

    // ===== Server Select =====

    function renderServerSelect() {
        serverSelect.innerHTML = '';
        for (var i = 0; i < serverConfigs.length; i++) {
            var opt = document.createElement('option');
            opt.value = serverConfigs[i].id;
            opt.textContent = serverConfigs[i].name;
            serverSelect.appendChild(opt);
        }

        // Restore selection, then update models without firing server change
        var currentId = NATIVE.getCurrentServerId();
        if (currentId) {
            serverSelect.value = currentId;
        }
        renderModelSelect(getSelectedConfig());
    }

    function onServerChange() {
        var config = getSelectedConfig();
        if (!config) return;

        NATIVE.selectServer(config.id);
        renderModelSelect(config);
    }

    serverSelect.addEventListener('change', onServerChange);

    // ===== Model Select =====

    function renderModelSelect(config) {
        if (!config || !config.models) {
            modelSelect.innerHTML = '<option>无模型</option>';
            return;
        }
        var models = config.models.split(';').map(function(m) { return m.trim(); }).filter(Boolean);
        modelSelect.innerHTML = '';
        for (var i = 0; i < models.length; i++) {
            var opt = document.createElement('option');
            opt.value = models[i];
            opt.textContent = models[i];
            modelSelect.appendChild(opt);
        }

        if (config.lastSelectedModel) {
            modelSelect.value = config.lastSelectedModel;
        }
    }

    function onModelChange() {
        NATIVE.selectModel(modelSelect.value);
    }

    modelSelect.addEventListener('change', onModelChange);

    // ===== Format Select =====

    function onFormatChange() {
        NATIVE.selectFormat(formatSelect.value);
    }

    formatSelect.addEventListener('change', onFormatChange);

    window.restoreConfigFormat = function(format) {
        for (var i = 0; i < formatSelect.options.length; i++) {
            if (formatSelect.options[i].value === format) {
                formatSelect.selectedIndex = i;
                break;
            }
        }
    };

    // ===== Deck Select =====

    function renderDeckSelect(decks) {
        deckSelect.innerHTML = '';
        if (!decks || decks.length === 0) {
            var opt = document.createElement('option');
            opt.value = '';
            opt.textContent = '无可用牌组';
            deckSelect.appendChild(opt);
            return;
        }
        for (var i = 0; i < decks.length; i++) {
            var opt = document.createElement('option');
            opt.value = decks[i].id;
            opt.textContent = decks[i].name;
            deckSelect.appendChild(opt);
        }

        var currentDeckId = NATIVE.getCurrentDeckId();
        if (currentDeckId) {
            deckSelect.value = String(currentDeckId);
        }
    }

    function onDeckChange() {
        var deckId = deckSelect.value;
        if (deckId) {
            NATIVE.selectDeck(deckId);
        }
    }

    deckSelect.addEventListener('change', onDeckChange);

    // ===== Public API for deck updates from Java =====

    window.updateDeckList = function(decks) {
        renderDeckSelect(decks);
    };

    // ===== Helpers =====

    function getSelectedConfig() {
        var id = serverSelect.value;
        for (var i = 0; i < serverConfigs.length; i++) {
            if (serverConfigs[i].id === id) return serverConfigs[i];
        }
        return null;
    }

    // ===== Init =====

    if (configContainer) {
        configContainer.classList.remove('visible');
    }
})();
