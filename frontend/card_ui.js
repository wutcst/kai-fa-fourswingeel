/**
 * 卡牌显示工具 — 效果文字生成 + 状态机制悬浮小窗
 * 所有翻卡/选卡/看卡页面共用
 */
const CARD_UI = {

  /** 状态机制详情（与 backend statuses.json 保持一致） */
  STATUS_INFO: {
    'VULNERABLE':   { name: '易伤',     desc: '受到的伤害增加 50%。',                                        decay: true },
    'WEAK':         { name: '虚弱',     desc: '造成的伤害减少 25%。',                                        decay: true },
    'FRAIL':        { name: '脆弱',     desc: '获得的格挡减少 25%。',                                        decay: true },
    'STRENGTH':     { name: '力量',     desc: '每层力量使攻击伤害增加 1 点。',                                decay: false },
    'DEXTERITY':    { name: '敏捷',     desc: '每层敏捷使获得的格挡增加 1 点。',                              decay: false },
    'POISON':       { name: '毒',       desc: '回合结束时，造成等同于毒层数的伤害，然后减少 1 层。',           decay: true },
    'REGENERATION': { name: '再生',     desc: '回合结束时，恢复等同于再生层数的生命，然后减少 1 层。',          decay: true }
  },

  /** 状态类型 → 中文名 */
  getStatusName(type) {
    return this.STATUS_INFO[type]?.name || type;
  },

  /** 根据卡牌数据生成效果描述文字 */
  getCardEffectText(card) {
    const parts = [];
    if (card.damage > 0) parts.push(`造成 ${card.damage} 点伤害`);
    if (card.block > 0)  parts.push(`获得 ${card.block} 点格挡`);
    if (card.applyStatusType) {
      const sn = this.getStatusName(card.applyStatusType);
      const isSelf = card.applyStatusTarget === 'SELF';
      parts.push(`${isSelf ? '自身获得' : '施加'} ${card.applyStatusCount} 层${sn}`);
    }
    return parts.join('，') || '无效果';
  },

  /**
   * 为 DOM 元素绑定悬浮小窗（状态机制详情）
   * @param {Element} el - 要绑定的元素
   * @param {string} applyStatusType - 状态类型 ID
   */
  bindTooltip(el, applyStatusType) {
    if (!applyStatusType) return;
    const info = this.STATUS_INFO[applyStatusType];
    if (!info) return;

    const TIP_ID = '_card_ui_tooltip';

    el.addEventListener('mouseenter', (e) => {
      let tip = document.getElementById(TIP_ID);
      if (!tip) {
        tip = document.createElement('div');
        tip.id = TIP_ID;
        tip.style.cssText = 'position:fixed;z-index:9999;pointer-events:none;max-width:260px;background:#0d1b2a;border:1px solid #34495e;border-radius:8px;padding:12px 14px;box-shadow:0 8px 24px rgba(0,0,0,0.6);display:none;font-family:Segoe UI,Tahoma,sans-serif;';
        document.body.appendChild(tip);
      }
      tip.innerHTML =
        `<div style="font-weight:bold;font-size:14px;color:#f1c40f;margin-bottom:6px;">${info.name}</div>` +
        `<div style="font-size:12px;color:#bdc3c7;line-height:1.5;margin-bottom:4px;">${info.desc}</div>` +
        (info.decay ? '<div style="font-size:11px;color:#e67e22;">每回合结束层数减一</div>' : '');
      tip.style.display = 'block';
      const x = e.clientX, y = e.clientY, w = window.innerWidth;
      tip.style.left = ((x + 280 > w) ? x - 270 : x + 15) + 'px';
      tip.style.top  = Math.min(y, window.innerHeight - 120) + 'px';
    });

    el.addEventListener('mouseleave', () => {
      const tip = document.getElementById(TIP_ID);
      if (tip) tip.style.display = 'none';
    });
  }
};
