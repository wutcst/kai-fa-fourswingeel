// save_helper.js - 全局存档读写辅助函数



async function loadSaveData(charId) {

    let data = null;

    try {

        // 🆕 移除 charId 参数，所有角色共用全局存档

        const resp = await fetch(`/api/load`);

        if (resp.ok) {

            data = await resp.json();

            // 如果存档中有 charId，以存档为准（保证角色一致性）

            if (data && data.charId) {

                charId = data.charId;

            }

        }

    } catch (e) {

        console.warn('后端不可用，降级使用本地数据', e);

    }



    if (!data || !data.charId) {

        // 降级逻辑：从 localStorage 读取

        data = {

            charId: charId,

            playerHp: parseInt(localStorage.getItem('playerHP')) || 70,

            maxHp: parseInt(localStorage.getItem('maxHP')) || 70,

            gold: parseInt(localStorage.getItem('gold')) || 0,

            deck: JSON.parse(localStorage.getItem('deck') || '[]'),

            relics: JSON.parse(localStorage.getItem('relics') || '[]'),

            visitedNodes: JSON.parse(localStorage.getItem('visitedNodes') || '["start"]'),

            currentNode: 'start',

            mapNodes: JSON.parse(localStorage.getItem('mapNodes') || '[]'),

            mapEdges: JSON.parse(localStorage.getItem('mapEdges') || '[]'),

            act: parseInt(localStorage.getItem('act')) || 1,   // 🆕 读取 act
            goldTotalEarned: parseInt(localStorage.getItem('goldTotalEarned')) || 0

        };

    }



    // 🆕 读档后立即同步到 localStorage，保证 status_bar.js 等组件拿到最新数据

    localStorage.setItem('char', data.charId || charId);

    localStorage.setItem('playerHP', data.playerHp);

    localStorage.setItem('maxHp', data.maxHp);

    localStorage.setItem('gold', data.gold);

    localStorage.setItem('deck', JSON.stringify(data.deck || []));

    localStorage.setItem('relics', JSON.stringify(data.relics || []));

    localStorage.setItem('visitedNodes', JSON.stringify(data.visitedNodes || ['start']));

    localStorage.setItem('mapNodes', JSON.stringify(data.mapNodes || []));

    localStorage.setItem('mapEdges', JSON.stringify(data.mapEdges || []));

    // 🆕 同步 act

    if (data.act !== undefined) {

        localStorage.setItem('act', data.act);

    }

    // 🆕 同步累计金币（gxyy 遗物用）

    if (data.goldTotalEarned !== undefined) {

        localStorage.setItem('goldTotalEarned', data.goldTotalEarned);

    }

    if (window.updateStatusBar) window.updateStatusBar();



    return data;

}



async function saveSaveData(data) {

    try {

        // 🆕 移除 URL 中的 charId 参数

        await fetch('/api/save', {

            method: 'POST',

            headers: { 'Content-Type': 'application/json' },

            body: JSON.stringify(data)

        });

    } catch (e) { console.error('保存到后端失败', e); }



    // 同步到 localStorage，兼容 status_bar.js 等旧组件

    localStorage.setItem('char', data.charId);

    localStorage.setItem('playerHP', data.playerHp);

    localStorage.setItem('maxHp', data.maxHp);

    localStorage.setItem('gold', data.gold);

    localStorage.setItem('deck', JSON.stringify(data.deck));

    localStorage.setItem('relics', JSON.stringify(data.relics));

    localStorage.setItem('visitedNodes', JSON.stringify(data.visitedNodes));

    // 🆕 同步地图数据到本地

    localStorage.setItem('mapNodes', JSON.stringify(data.mapNodes || []));

    localStorage.setItem('mapEdges', JSON.stringify(data.mapEdges || []));

    // 🆕 同步 act

    if (data.act !== undefined) {

        localStorage.setItem('act', data.act);

    }



    if (window.updateStatusBar) window.updateStatusBar();

}