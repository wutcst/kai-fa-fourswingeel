// status_bar.js
(function() {
  // 从 localStorage 读取数据
  function getPlayerData() {
    return {
      char: localStorage.getItem('char') || '1',
      hp: parseInt(localStorage.getItem('playerHP')) || 70,
      maxHp: parseInt(localStorage.getItem('maxHP')) || 70,
      gold: parseInt(localStorage.getItem('gold')) || 0,
      relics: JSON.parse(localStorage.getItem('relics') || '[]'),
      deck: JSON.parse(localStorage.getItem('deck') || '[]')
    };
  }

  // 更新显示
  function updateBar() {
    const data = getPlayerData();
    const charNames = { '1': '铁甲战士', '2': '??? ' };
    document.getElementById('bar-char').textContent = charNames[data.char] || '未知';
    document.getElementById('bar-hp').textContent = `${data.hp}/${data.maxHp}`;
    document.getElementById('bar-gold').textContent = data.gold;
  }

  // 创建 HTML 结构
  const barHTML = `
    <div id="status-bar" style="
      position: fixed; top: 0; left: 0; width: 100%; height: 40px;
      background: rgba(10,10,26,0.95); backdrop-filter: blur(10px);
      display: flex; align-items: center; justify-content: space-around;
      padding: 0 20px; z-index: 9999; border-bottom: 1px solid #333;
      font-size: 14px; color: #e0e0e0; box-sizing: border-box;
    ">
      <span>👤 <span id="bar-char">铁甲战士</span></span>
      <span>❤️ <span id="bar-hp">70/70</span></span>
      <span>💰 <span id="bar-gold">0</span></span>
      <span class="bar-btn" id="relic-btn">📿 遗物</span>
      <span class="bar-btn" id="deck-btn">🃏 卡组</span>
    </div>
  `;

  // 插入到 body 最前面
  document.body.insertAdjacentHTML('afterbegin', barHTML);

  // 按钮事件（暂为占位）
  document.getElementById('relic-btn').addEventListener('click', () => {
    alert('遗物页面即将开放！');
  });
  document.getElementById('deck-btn').addEventListener('click', () => {
    alert('卡组页面即将开放！');
  });

  // 初始化更新
  updateBar();

  // 公开更新函数供其他页面调用
  window.updateStatusBar = updateBar;
})();