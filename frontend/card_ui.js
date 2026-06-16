const CARD_UI = {
  STATUS_INFO: {
    'VULNERABLE':   { name: '易伤',     desc: '受到的伤害增加 50%。',                                        decay: true },
    'WEAK':         { name: '虚弱',     desc: '造成的伤害减少 25%。',                                        decay: true },
    'FRAIL':        { name: '脆弱',     desc: '获得的格挡减少 25%。',                                        decay: true },
    'STRENGTH':     { name: '力量',     desc: '每层力量使攻击伤害增加 1 点。',                                decay: false },
    'DEXTERITY':    { name: '敏捷',     desc: '每层敏捷使获得的格挡增加 1 点。',                              decay: false },
    'POISON':       { name: '毒',       desc: '回合结束时，造成等同于毒层数的伤害，然后减少 1 层。',           decay: true },
    'REGENERATION': { name: '再生',     desc: '回合结束时，恢复等同于再生层数的生命，然后减少 1 层。',          decay: true },
    'RITUAL':       { name: '仪式',     desc: '回合开始时，获得 1 点力量。',                                  decay: false }
  },
  RARITY_COLORS: { 'START': '#999999', 'COMMON': '#bdc3c7', 'UNCOMMON': '#5dade2', 'RARE': '#f1c40f' },
  getStatusName(type) { return this.STATUS_INFO[type]?.name || type; },

  getCardEffectText(card) {
    const parts = [];
    if (card.damage > 0) {
      // 🆕 处理多段伤害文本
      if (card.multiHitCount > 1) parts.push(`造成 ${card.damage} 点伤害 ${card.multiHitCount} 次`);
      else parts.push(`造成 ${card.damage} 点伤害`);
    }
    if (card.block > 0)  parts.push(`获得 ${card.block} 点格挡`);
    if (card.selfDamage > 0) parts.push(`失去 ${card.selfDamage} 点生命`);
    if (card.energyGain > 0) parts.push(`获得 ${card.energyGain} 点能量`);
    if (card.drawCount > 0) parts.push(`抽 ${card.drawCount} 张牌`);
    if (card.applyStatusType) {
      const sn = this.getStatusName(card.applyStatusType);
      const isSelf = card.applyStatusTarget === 'SELF';
      parts.push(`${isSelf ? '自身获得' : '施加'} ${card.applyStatusCount} 层${sn}`);
    }
    return parts.join('，') || '无效果';
  },

  getCardTypeLabel(card) {
    if (card.type === 'ATTACK') return '攻击';
    if (card.type === 'SKILL')  return '防御';
    if (card.type === 'POWER')  return '能力';
    return '未知';
  },
  getCardTypeClass(card) {
    if (card.type === 'ATTACK') return 'attack';
    if (card.type === 'SKILL')  return 'skill';
    if (card.type === 'POWER')  return 'power';
    return '';
  },
  getCardCharStyle(card) {
    if (!card) return 'linear-gradient(135deg, #667eea, #764ba2)';
    const charId = (card.charId && card.charId !== '') ? card.charId : '1';
    if (charId === '0') return 'linear-gradient(135deg, #808080, #A9A9A9)';
    if (charId === '2') return 'linear-gradient(135deg, #2ecc71, #27ae60)'; // 静默猎手-绿色
    return 'linear-gradient(135deg, #8B0000, #B22222)';
  },
  getRarityBorderColor(card) {
    const rarity = (card && card.rarity) ? card.rarity : 'COMMON';
    return this.RARITY_COLORS[rarity] || '#bdc3c7';
  },
  decorateCardElement(el, card) {
    el.style.background = this.getCardCharStyle(card);
    const typeClass = this.getCardTypeClass(card);
    if (typeClass) el.classList.add(typeClass);
    const borderColor = this.getRarityBorderColor(card);
    el.style.borderColor = borderColor; el.style.borderWidth = '3px';
    this.bindTooltip(el, card.applyStatusType);
  },
  bindTooltip(el, applyStatusType) {
    if (!applyStatusType) return;
    const info = this.STATUS_INFO[applyStatusType];
    if (!info) return;
    const TIP_ID = '_card_ui_tooltip';
    el.addEventListener('mouseenter', (e) => {
      let tip = document.getElementById(TIP_ID);
      if (!tip) {
        tip = document.createElement('div'); tip.id = TIP_ID;
        tip.style.cssText = 'position:fixed;z-index:9999;pointer-events:none;max-width:260px;background:#0d1b2a;border:1px solid #34495e;border-radius:8px;padding:12px 14px;box-shadow:0 8px 24px rgba(0,0,0,0.6);display:none;font-family:Segoe UI,Tahoma,sans-serif;';
        document.body.appendChild(tip);
      }
      tip.innerHTML = `<div style="font-weight:bold;font-size:14px;color:#f1c40f;margin-bottom:6px;">${info.name}</div><div style="font-size:12px;color:#bdc3c7;line-height:1.5;margin-bottom:4px;">${info.desc}</div>` + (info.decay ? '<div style="font-size:11px;color:#e67e22;">每回合结束层数减一</div>' : '');
      tip.style.display = 'block';
      const x = e.clientX, y = e.clientY, w = window.innerWidth;
      tip.style.left = ((x + 280 > w) ? x - 270 : x + 15) + 'px';
      tip.style.top  = Math.min(y, window.innerHeight - 120) + 'px';
    });
    el.addEventListener('mouseleave', () => { const tip = document.getElementById(TIP_ID); if (tip) tip.style.display = 'none'; });
  }
};
