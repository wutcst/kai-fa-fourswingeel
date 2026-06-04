// status_bar.js
(function() {
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
      <span class="bar-btn" id="relic-btn" style="cursor:pointer;">📿 遗物</span>
      <span class="bar-btn" id="deck-btn" style="cursor:pointer;">🃏 卡组</span>
    </div>
  `;

  document.body.insertAdjacentHTML('afterbegin', barHTML);

  // 遗物按钮：避免在自身页面反复跳转
  document.getElementById('relic-btn').addEventListener('click', () => {
    const currentPage = window.location.pathname.split('/').pop();
    if (currentPage !== 'relics.html') {
      window.location.href = 'relics.html';
    }
  });

  // 卡组按钮
  document.getElementById('deck-btn').addEventListener('click', () => {
    const currentPage = window.location.pathname.split('/').pop();
    if (currentPage !== 'deck.html') {
      window.location.href = 'deck.html';
    }
  });

  updateBar();
  window.updateStatusBar = updateBar;
})();