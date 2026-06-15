// save_helper.js - 全局存档读写辅助函数
async function loadSaveData(charId) {
    try {
        const resp = await fetch(`/api/load?charId=${charId}`);
        if (resp.ok) return await resp.json();
    } catch (e) { console.warn('后端不可用，降级使用本地数据', e); }
    // 降级逻辑：从 localStorage 读取
    return {
        charId: charId,
        playerHp: parseInt(localStorage.getItem('playerHP')) || 70,
        maxHp: parseInt(localStorage.getItem('maxHP')) || 70,
        gold: parseInt(localStorage.getItem('gold')) || 0,
        deck: JSON.parse(localStorage.getItem('deck') || '[]'),
        relics: JSON.parse(localStorage.getItem('relics') || '[]'),
        visitedNodes: JSON.parse(localStorage.getItem('visitedNodes') || '["start"]'),
        currentNode: 'start'
    };
}

async function saveSaveData(data) {
    try {
        await fetch('/api/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
    } catch (e) { console.error('保存到后端失败', e); }
    
    // 同步到 localStorage，以兼容 status_bar.js 等旧组件
    localStorage.setItem('playerHP', data.playerHp);
    localStorage.setItem('maxHP', data.maxHp);
    localStorage.setItem('gold', data.gold);
    localStorage.setItem('deck', JSON.stringify(data.deck));
    localStorage.setItem('relics', JSON.stringify(data.relics));
    localStorage.setItem('visitedNodes', JSON.stringify(data.visitedNodes));
    if (window.updateStatusBar) window.updateStatusBar();
}