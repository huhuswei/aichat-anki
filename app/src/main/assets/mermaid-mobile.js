/**
 * mermaid-mobile.js
 * 为移动端提供mermaid图表的交互功能
 * 包括点击显示大图和双指缩放
 */

document.addEventListener('DOMContentLoaded', function() {
    // 创建模态框元素
    const modal = document.createElement('div');
    modal.className = 'mermaid-modal';
    
    const modalContent = document.createElement('div');
    modalContent.className = 'mermaid-modal-content';
    
    const closeBtn = document.createElement('div');
    closeBtn.className = 'mermaid-close';
    closeBtn.innerHTML = '&times;';
    
    const zoomContainer = document.createElement('div');
    zoomContainer.className = 'mermaid-zoom-container';
    
    const zoomContent = document.createElement('div');
    zoomContent.className = 'mermaid-zoom-content';
    
    // 缩放控制按钮
    const zoomControls = document.createElement('div');
    zoomControls.className = 'zoom-controls';
    
    const zoomOutBtn = document.createElement('button');
    zoomOutBtn.className = 'zoom-button zoom-out';
    zoomOutBtn.innerText = '-';
    
    const zoomInfo = document.createElement('div');
    zoomInfo.className = 'zoom-info';
    zoomInfo.innerText = '100%';
    
    const zoomInBtn = document.createElement('button');
    zoomInBtn.className = 'zoom-button zoom-in';
    zoomInBtn.innerText = '+';
    
    const resetZoomBtn = document.createElement('button');
    resetZoomBtn.className = 'zoom-button zoom-reset';
    resetZoomBtn.innerText = '↺';
    
    // 添加提示文本
    const hintText = document.createElement('div');
    hintText.className = 'mermaid-hint';
    hintText.innerText = '双击重置缩放 | 单指拖动 | 双指缩放';
    
    // 组装模态框
    zoomControls.appendChild(zoomOutBtn);
    zoomControls.appendChild(zoomInfo);
    zoomControls.appendChild(zoomInBtn);
    zoomControls.appendChild(resetZoomBtn);
    
    zoomContainer.appendChild(zoomContent);
    zoomContainer.appendChild(hintText);
    
    modalContent.appendChild(zoomContainer);
    
    modal.appendChild(closeBtn);
    modal.appendChild(modalContent);
    modal.appendChild(zoomControls);
    
    document.body.appendChild(modal);
    
    // 缩放状态
    let currentScale = 1;
    let startScale = 1;
    let initialDistance = 0;
    let isDragging = false;
    let lastX = 0;
    let lastY = 0;
    let zoomContentPosition = { x: 0, y: 0 }; // 跟踪内容位置
    
    // 用于跟踪已经绑定事件的元素
    const boundElements = new WeakSet();
    
    // 双击检测变量
    let lastTap = 0;
    let tapTimeout;
    
    // 监听所有mermaid图表的点击事件
    function setupMermaidInteractions() {
        const mermaidDivs = document.querySelectorAll('.mermaid');
        
        mermaidDivs.forEach(div => {
            // 检查元素是否已经绑定了事件，避免重复绑定
            if (!boundElements.has(div)) {
                div.addEventListener('click', handleMermaidClick);
                boundElements.add(div);
            }
        });
    }
    
    // 抽取点击处理函数，便于解绑和重用
    function handleMermaidClick(e) {
        // 克隆当前图表到模态框，使用深度克隆
        const clone = this.cloneNode(true);
        
        // 清空之前的内容
        zoomContent.innerHTML = '';
        zoomContent.appendChild(clone);
        
        // 重置缩放和位置
        resetZoomAndPosition();
        
        // 显示模态框
        modal.classList.add('show');
        document.body.style.overflow = 'hidden'; // 防止背景滚动
        
        // 确保图表内容能填充整个视口
        ensureContentSize();
        
        // 显示提示，然后渐隐
        hintText.classList.add('show');
        setTimeout(() => {
            hintText.classList.remove('show');
        }, 3000);
    }
    
    // 重置缩放和位置状态
    function resetZoomAndPosition() {
        currentScale = 1;
        zoomContentPosition = { x: 0, y: 0 };
        zoomContent.style.transform = 'scale(1) translate(0px, 0px)';
        zoomInfo.innerText = '100%';
    }
    
    // 确保内容尺寸足够大，能够支持平移
    function ensureContentSize() {
        // 延迟执行，等待mermaid图表渲染完成
        setTimeout(() => {
            const mermaidSvg = zoomContent.querySelector('svg');
            if (mermaidSvg) {
                // 保存原始宽高比
                const originalWidth = mermaidSvg.getAttribute('width') || mermaidSvg.getBoundingClientRect().width;
                const originalHeight = mermaidSvg.getAttribute('height') || mermaidSvg.getBoundingClientRect().height;
                const aspectRatio = originalWidth / originalHeight;
                
                // 获取容器尺寸
                const containerBounds = zoomContainer.getBoundingClientRect();
                
                // 确定SVG尺寸，保持宽高比
                let svgWidth, svgHeight;
                
                if (containerBounds.width / containerBounds.height > aspectRatio) {
                    // 容器更宽，以高度为基准
                    svgHeight = Math.min(containerBounds.height * 0.9, originalHeight);
                    svgWidth = svgHeight * aspectRatio;
                } else {
                    // 容器更高，以宽度为基准
                    svgWidth = Math.min(containerBounds.width * 0.9, originalWidth);
                    svgHeight = svgWidth / aspectRatio;
                }
                
                // 设置尺寸
                mermaidSvg.style.width = `${svgWidth}px`;
                mermaidSvg.style.height = `${svgHeight}px`;
                mermaidSvg.style.maxWidth = '100%';
                mermaidSvg.style.maxHeight = '100%';
                
                // 为SVG添加双击事件
                mermaidSvg.addEventListener('dblclick', function(e) {
                    resetZoomAndPosition();
                    e.preventDefault();
                    e.stopPropagation();
                });
            }
        }, 200);
    }
    
    // 设置模态框关闭事件
    closeBtn.addEventListener('click', closeModal);
    
    // 点击模态框背景关闭
    modal.addEventListener('click', function(e) {
        if (e.target === modal) {
            closeModal();
        }
    });
    
    // 关闭模态框并清理资源
    function closeModal() {
        modal.classList.remove('show');
        document.body.style.overflow = '';
        
        // 延迟清理DOM，让动画先完成
        setTimeout(() => {
            zoomContent.innerHTML = '';
        }, 300);
    }
    
    // 缩放按钮事件
    zoomInBtn.addEventListener('click', function() {
        setZoom(currentScale + 0.2);
    });
    
    zoomOutBtn.addEventListener('click', function() {
        setZoom(currentScale - 0.2);
    });
    
    resetZoomBtn.addEventListener('click', function() {
        resetZoomAndPosition();
    });
    
    // 设置缩放值和更新UI
    function setZoom(scale) {
        // 限制缩放范围
        currentScale = Math.min(Math.max(scale, 0.5), 5);
        updateTransform();
        zoomInfo.innerText = `${Math.round(currentScale * 100)}%`;
    }
    
    // 更新变换
    function updateTransform() {
        zoomContent.style.transform = `scale(${currentScale}) translate(${zoomContentPosition.x}px, ${zoomContentPosition.y}px)`;
    }
    
    // 触摸事件处理
    let touchStartX = 0, touchStartY = 0;
    
    // 检测双击
    function checkDoubleTap(e) {
        const now = new Date().getTime();
        const timeDiff = now - lastTap;
        
        if (timeDiff < 300 && timeDiff > 0) {
            // 双击检测到
            clearTimeout(tapTimeout);
            resetZoomAndPosition();
            return true;
        } else {
            // 不是双击，设置单击延迟
            lastTap = now;
            return false;
        }
    }
    
    // 触摸事件 - 双指缩放
    zoomContainer.addEventListener('touchstart', function(e) {
        touchStartX = e.touches[0].clientX;
        touchStartY = e.touches[0].clientY;
        
        if (e.touches.length === 2) {
            // 阻止默认行为，避免页面滚动
            e.preventDefault();
            
            // 获取两个触摸点之间的初始距离
            initialDistance = getDistance(
                e.touches[0].clientX, e.touches[0].clientY,
                e.touches[1].clientX, e.touches[1].clientY
            );
            startScale = currentScale;
            isDragging = false;
        } else if (e.touches.length === 1) {
            // 检查是否是双击
            if (checkDoubleTap(e)) {
                e.preventDefault();
                return;
            }
            
            // 单指拖动开始
            isDragging = true;
            lastX = e.touches[0].clientX;
            lastY = e.touches[0].clientY;
        }
    }, { passive: false });
    
    zoomContainer.addEventListener('touchmove', function(e) {
        if (e.touches.length === 2) {
            // 阻止默认行为，避免页面滚动
            e.preventDefault();
            
            // 计算新的距离
            const currentDistance = getDistance(
                e.touches[0].clientX, e.touches[0].clientY,
                e.touches[1].clientX, e.touches[1].clientY
            );
            
            // 计算缩放比例
            const scaleChange = currentDistance / initialDistance;
            const newScale = startScale * scaleChange;
            
            setZoom(newScale);
        } else if (e.touches.length === 1 && isDragging) {
            // 单指拖动处理 - 使用translate而非scroll
            const currentX = e.touches[0].clientX;
            const currentY = e.touches[0].clientY;
            
            // 计算拖动距离，考虑缩放因素
            const deltaX = (currentX - lastX) / currentScale;
            const deltaY = (currentY - lastY) / currentScale;
            
            // 更新内容位置
            zoomContentPosition.x += deltaX;
            zoomContentPosition.y += deltaY;
            
            // 应用变换
            updateTransform();
            
            // 更新上次位置
            lastX = currentX;
            lastY = currentY;
            
            // 阻止默认行为，避免页面滚动
            e.preventDefault();
        }
    }, { passive: false });
    
    zoomContainer.addEventListener('touchend', function(e) {
        if (e.touches.length === 0) {
            // 所有手指都离开了屏幕
            initialDistance = 0;
            isDragging = false;
            
            // 检查是否是轻触（点击）
            const endX = e.changedTouches[0].clientX;
            const endY = e.changedTouches[0].clientY;
            const distMoved = Math.sqrt(Math.pow(endX - touchStartX, 2) + Math.pow(endY - touchStartY, 2));
            
            // 如果移动距离很小，并且是单指操作，认为是点击
            if (distMoved < 10 && e.changedTouches.length === 1) {
                // 延迟处理单击事件，以便能检测到双击
                tapTimeout = setTimeout(() => {
                    // 单击，切换控制按钮显示
                    zoomControls.classList.toggle('hidden');
                }, 300);
            }
        }
    });
    
    // 计算两点之间的距离
    function getDistance(x1, y1, x2, y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
    
    // 使用防抖函数优化MutationObserver的回调
    function debounce(func, wait) {
        let timeout;
        return function() {
            const context = this;
            const args = arguments;
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(context, args), wait);
        };
    }
    
    // 防抖处理的setupMermaidInteractions
    const debouncedSetup = debounce(setupMermaidInteractions, 100);
    
    // 监听屏幕方向变化，调整内容
    window.addEventListener('orientationchange', function() {
        // 延迟执行，等待屏幕旋转完成
        setTimeout(ensureContentSize, 300);
    });
    
    // MutationObserver 监听DOM变化，确保在动态添加的mermaid图表上也绑定事件
    const observer = new MutationObserver(function(mutations) {
        let shouldSetup = false;
        
        mutations.forEach(function(mutation) {
            // 只在真正添加了新节点时处理
            if (mutation.addedNodes.length) {
                for (let i = 0; i < mutation.addedNodes.length; i++) {
                    const node = mutation.addedNodes[i];
                    // 检查是否添加了mermaid相关元素
                    if (node.nodeType === 1 && 
                        (node.classList?.contains('mermaid') || 
                         node.querySelector?.('.mermaid'))) {
                        shouldSetup = true;
                        break;
                    }
                }
            }
        });
        
        if (shouldSetup) {
            debouncedSetup();
        }
    });
    
    observer.observe(document.body, { childList: true, subtree: true });
    
    // 初始设置
    setupMermaidInteractions();
    
    // 页面卸载时清理
    window.addEventListener('beforeunload', function() {
        observer.disconnect();
    });
}); 