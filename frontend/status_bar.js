// status_bar.js - 终极防错版
(function() {
    // 防止重复初始化
    if (window.__statusBarInitialized) return;
    window.__statusBarInitialized = true;

    function getPlayerData() {
        return {
            char: localStorage.getItem('char') || '1',
            hp: localStorage.getItem('playerHP') || '70',
            maxHp: localStorage.getItem('maxHP') || '70',
            gold: localStorage.getItem('gold') || '0'
        };
    }

    function updateBar() {
        const data = getPlayerData();
        const charNames = { '1': '铁甲战士', '2': '???' };
        
        const charEl = document.getElementById('bar-char');
        const hpEl = document.getElementById('bar-hp');
        const goldEl = document.getElementById('bar-gold');
        
        if (charEl) charEl.textContent = charNames[data.char] || '未知';
        if (hpEl) hpEl.textContent = `${data.hp}/${data.maxHp}`;
        if (goldEl) goldEl.textContent = data.gold;
    }

    function createStatusBar() {
        // 如果已经存在，只更新数据
        if (document.getElementById('status-bar')) {
            updateBar();
            return;
        }

        // 使用 createElement 比 insertAdjacentHTML 更稳定
        const bar = document.createElement('div');
        bar.id = 'status-bar';
        bar.style.cssText = `
            position: fixed; top: 0; left: 0; width: 100%; height: 40px;
            background: rgba(10,10,26,0.95); backdrop-filter: blur(10px);
            display: flex; align-items: center; justify-content: space-around;
            padding: 0 20px; z-index: 99999; border-bottom: 1px solid #333;
            font-size: 14px; color: #e0e0e0; box-sizing: border-box;
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        `;
        
        bar.innerHTML = `
            <span>👤 <span id="bar-char">铁甲战士</span></span>
            <span>❤️ <span id="bar-hp">70/70</span></span>
            <span>💰 <span id="bar-gold">0</span></span>
            <span id="relic-btn" style="cursor:pointer; color: #f1c40f;">📿 遗物</span>
            <span id="deck-btn" style="cursor:pointer; color: #3498db;">🃏 卡组</span>
        `;
        
        // 确保 body 存在再追加
        if (document.body) {
            document.body.appendChild(bar);
            
            document.getElementById('relic-btn').onclick = () => {
                if (!window.location.pathname.includes('relics.html')) window.location.href = 'relics.html';
            };
            document.getElementById('deck-btn').onclick = () => {
                if (!window.location.pathname.includes('deck.html')) window.location.href = 'deck.html';
            };

            updateBar();
            window.updateStatusBar = updateBar;
        }
    }

    // ✅ 核心防错：判断 DOM 是否加载完成
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', createStatusBar);
    } else {
        createStatusBar();
    }
})();