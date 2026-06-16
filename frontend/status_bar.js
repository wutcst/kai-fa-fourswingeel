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
        const charNames = { '1': '🦾 铁甲战士', '2': '❓ ???' };
        
        const charEl = document.getElementById('bar-char');
        const hpEl = document.getElementById('bar-hp');
        const goldEl = document.getElementById('bar-gold');
        
        if (charEl) charEl.textContent = charNames[data.char] || '未知';
        if (hpEl) hpEl.innerHTML = `❤️ ${data.hp}/${data.maxHp}`;
        if (goldEl) goldEl.innerHTML = `💰 ${data.gold}`;
    }

    // ---------- 弹出层辅助函数 ----------
    function createOverlay(contentHtml) {
        // 移除旧覆盖层
        const old = document.getElementById('spire-overlay');
        if (old) old.remove();

        const overlay = document.createElement('div');
        overlay.id = 'spire-overlay';
        overlay.style.cssText = `
            position: fixed; top:0; left:0; width:100%; height:100%;
            background: rgba(0,0,0,0.7); z-index: 10000;
            display: flex; justify-content: center; align-items: center;
            font-family: 'Segoe UI', Tahoma, sans-serif;
        `;
        overlay.innerHTML = contentHtml;

        // 点击背景关闭
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) overlay.remove();
        });

        document.body.appendChild(overlay);
    }

    function closeOverlay() {
        const o = document.getElementById('spire-overlay');
        if (o) o.remove();
    }

    // ---------- 确保初始遗物存在 ----------
    function ensureInitialRelics() {
        const char = localStorage.getItem('char') || '1';
        let relicsStr = localStorage.getItem('relics');
        // 如果不存在或为空数组，并且是铁甲战士，则补充燃烧之血
        if (!relicsStr || relicsStr === '[]') {
            if (char === '1') {
                localStorage.setItem('relics', JSON.stringify(['burning_blood']));
            }
        }
    }

    // ---------- 显示遗物（仅显示已拥有的） ----------
    function showRelicPopup() {
        const playerData = getPlayerData();
        const charId = playerData.char;

        // 确保初始遗物已记录
        ensureInitialRelics();

        let ownedRelicIds = [];
        try {
            const saved = localStorage.getItem('relics');
            if (saved) ownedRelicIds = JSON.parse(saved);
        } catch(e) {}

        if (ownedRelicIds.length === 0) {
            createOverlay(`
                <div style="background:#1a1a2e; border:2px solid #f1c40f; border-radius:16px; padding:32px; color:#ecf0f1; text-align:center;">
                    <p>你还没有任何遗物。</p>
                    <button style="padding:10px 32px; background:#f1c40f; border:none; border-radius:8px; font-size:16px; cursor:pointer;" onclick="document.getElementById('spire-overlay').remove();">关闭</button>
                </div>
            `);
            return;
        }

        // 从后端获取所有遗物配置，用于显示名称和描述
        fetch('/api/relics')
            .then(r => r.json())
            .then(allRelics => {
                // 构建 ID -> 对象 的映射
                const relicMap = {};
                allRelics.forEach(r => relicMap[r.id] = r);

                let html = `
                    <div style="background:#1a1a2e; border:2px solid #f1c40f; border-radius:16px; max-width:500px; width:90%; max-height:80vh; overflow-y:auto; padding:24px; color:#ecf0f1;">
                        <h2 style="margin-top:0;color:#f1c40f;">🧬 我的遗物 (${ownedRelicIds.length})</h2>
                        <div style="display:flex; flex-direction:column; gap:12px;">
                `;
                ownedRelicIds.forEach(id => {
                    const r = relicMap[id];
                    if (r) {
                        html += `
                            <div style="background:#2c3e50; padding:14px; border-radius:8px; border-left:4px solid ${rarityColor(r.rarity)};">
                                <div style="font-weight:bold;">${r.name}</div>
                                <div style="font-size:0.85em; color:#bdc3c7;">${r.description || '无描述'}</div>
                            </div>
                        `;
                    } else {
                        // 如果后端没有该遗物记录，仍然显示 id
                        html += `
                            <div style="background:#2c3e50; padding:14px; border-radius:8px; border-left:4px solid #95a5a6;">
                                <div style="font-weight:bold;">未知遗物 (${id})</div>
                            </div>
                        `;
                    }
                });
                html += `
                        </div>
                        <div style="margin-top:16px; text-align:center;">
                            <button style="padding:10px 32px; background:#f1c40f; border:none; border-radius:8px; font-size:16px; cursor:pointer;" onclick="document.getElementById('spire-overlay').remove();">关闭</button>
                        </div>
                    </div>
                `;
                createOverlay(html);
            })
            .catch(err => {
                console.warn('获取遗物数据失败', err);
                // 降级：仅显示 id 列表
                let simpleHtml = `
                    <div style="background:#1a1a2e; border:2px solid #f1c40f; border-radius:16px; padding:24px; color:#ecf0f1; text-align:center;">
                        <h2 style="color:#f1c40f;">🧬 我的遗物 (${ownedRelicIds.length})</h2>
                        <div>${ownedRelicIds.join('、')}</div>
                        <button style="margin-top:16px; padding:10px 32px; background:#f1c40f; border:none; border-radius:8px; font-size:16px; cursor:pointer;" onclick="document.getElementById('spire-overlay').remove();">关闭</button>
                    </div>
                `;
                createOverlay(simpleHtml);
            });
    }

    function rarityColor(rarity) {
        const colors = { 'COMMON': '#95a5a6', 'UNCOMMON': '#5dade2', 'RARE': '#f1c40f', 'STARTER': '#999999', 'SPECIAL': '#9b59b6' };
        return colors[rarity] || '#95a5a6';
    }

    // ---------- 显示卡组（包含每张牌的完整效果） ----------
    function showDeckPopup() {
        const playerData = getPlayerData();

        // 从 localStorage 读取卡组（与 save_helper 兼容）
        let deck = [];
        try {
            const str = localStorage.getItem('deck');
            if (str) deck = JSON.parse(str);
        } catch(e) {}

        let html = `
            <div style="background:#1a1a2e; border:2px solid #f1c40f; border-radius:16px; max-width:700px; width:90%; max-height:80vh; overflow-y:auto; padding:24px; color:#ecf0f1;">
                <h2 style="margin-top:0;color:#f1c40f;">🃏 当前卡组 (${deck.length} 张)</h2>
                <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px;">
        `;
        if (deck.length === 0) {
            html += `<div style="grid-column:1/3; text-align:center; color:#bdc3c7;">卡组为空</div>`;
        } else {
            deck.forEach(card => {
                const typeLabel = card.type === 'ATTACK' ? '⚔️' : card.type === 'SKILL' ? '🛡️' : '🌟';
                // 获取完整的卡牌效果文字（包含伤害、格挡、抽牌、状态、自伤、能量、多段等）
                const effectText = CARD_UI.getCardEffectText(card);
                // 费用、伤害、格挡等简单信息作为辅助
                const additionalInfo = [];
                if (card.cost !== undefined) additionalInfo.push(`费用:${card.cost}`);
                if (card.damage > 0 && card.multiHitCount > 1) additionalInfo.push(`伤害:${card.damage}×${card.multiHitCount}`);
                else if (card.damage > 0) additionalInfo.push(`伤害:${card.damage}`);
                if (card.block > 0) additionalInfo.push(`格挡:${card.block}`);
                // selfDamage/energyGain 已经包含在 effectText 中, 无需重复

                html += `
                    <div style="background:#2c3e50; padding:10px; border-radius:8px; border-left:4px solid ${rarityColor(card.rarity || 'COMMON')};">
                        <div style="font-weight:bold;">${typeLabel} ${card.name}</div>
                        <div style="font-size:0.85em; color:#bdc3c7; margin-top:4px;">${additionalInfo.join(' | ')}</div>
                        <div style="font-size:0.8em; color:#ecf0f1; margin-top:6px; line-height:1.4;">${effectText}</div>
                    </div>
                `;
            });
        }
        html += `
                </div>
                <div style="margin-top:16px; text-align:center;">
                    <button style="padding:10px 32px; background:#f1c40f; border:none; border-radius:8px; font-size:16px; cursor:pointer;" onclick="document.getElementById('spire-overlay').remove();">关闭</button>
                </div>
            </div>
        `;
        createOverlay(html);
    }

    // ** 对外暴露更新函数，供 save_helper.js 调用 **
    window.updateStatusBar = updateBar;

    function createStatusBar() {
        // 确保初始遗物已存在
        ensureInitialRelics();

        // 如果已经存在，只更新数据
        if (document.getElementById('status-bar')) {
            updateBar();
            return;
        }

        // 使用 createElement 比 insertAdjacentHTML 更稳定
        const bar = document.createElement('div');
        bar.id = 'status-bar';
        bar.style.cssText = `
            position: fixed; top:0; left:0; width:100%; z-index:9999;
            background: rgba(20,20,40,0.9);
            display: flex; align-items: center; justify-content: space-between;
            padding: 8px 20px; box-sizing: border-box;
            font-family: 'Segoe UI', Tahoma, sans-serif;
            font-size: 14px; color: #ecf0f1;
            backdrop-filter: blur(4px);
            border-bottom: 1px solid #f1c40f;
        `;

        bar.innerHTML = `
            <div style="display:flex; align-items:center; gap:20px;">
                <span id="bar-char">🦾 铁甲战士</span>
                <span id="bar-hp">❤️ 70/70</span>
                <span id="bar-gold">💰 0</span>
            </div>
            <div style="display:flex; gap:12px;">
                <button id="relic-btn" style="padding:6px 14px; background:#34495e; border:1px solid #f1c40f; border-radius:6px; color:#ecf0f1; cursor:pointer;">🧬 遗物</button>
                <button id="deck-btn" style="padding:6px 14px; background:#34495e; border:1px solid #f1c40f; border-radius:6px; color:#ecf0f1; cursor:pointer;">🃏 卡组</button>
            </div>
        `;

        if (document.body) {
            document.body.appendChild(bar);
            
            document.getElementById('relic-btn').onclick = showRelicPopup;
            document.getElementById('deck-btn').onclick = showDeckPopup;

            updateBar();
        }
    }

    // 等待 DOM 加载完成再创建
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', createStatusBar);
    } else {
        createStatusBar();
    }
})();
