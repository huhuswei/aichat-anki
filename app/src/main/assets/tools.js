/**
 * 解析和替换文本中的音频标签
 * @param {string} text - 包含{{}}标签的文本
 * @returns {string} 替换后的文本
 */
function processAudioTags(text) {
    // 匹配 {{...}} 模式的正则表达式
    const audioTagRegex = /\{\{([^}]+)\}\}/g;

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
        // 格式: {{word|audio}}
        params.tagType = parts[1];
    } else if (parts.length >= 3) {
        // 格式: {{word|voicename|langid|urlType|tagType}}
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
    const baseUrl = 'https://api.xixikala.com/api/v2/speech/speakweb';
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
        return `{{${word}|audio}}`;
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
        return `{{${word}|${voicename}|${langid}|${urlType}|audio}}`;
    }
};

// 测试函数
function testAudioTagProcessing() {
    console.log('=== 音频标签处理测试 ===\n');

    const testCases = [
        // 简写格式
        '这是一个测试：{{hello|audio}}，请听发音。',

        // 完整格式
        '英文单词：{{world|en_uk_male|eng|ou|audio}}',

        // 中文测试
        '中文词语：{{你好|zh_cn_female|chn|ou|audio}}',

        // 混合格式
        `学习单词：
{{apple|audio}} - 苹果
{{banana|en_uk_male|eng|ou|audio}} - 香蕉
{{你好世界|zh_cn_male|chn|ou|audio}} - Hello World`,

        // 复杂文本
        '句子：{{The quick brown fox|en_us_male|eng|ou|audio}} jumps over the lazy dog.'
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