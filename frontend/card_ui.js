const CARD_UI = {
  STATUS_INFO: {
    'VULNERABLE':   { name: '易伤',     desc: '受到的伤害增加 50%。',                                        decay: true },
    'WEAK':         { name: '虚弱',     desc: '造成的伤害减少 25%。',                                        decay: true },
    'FRAIL':        { name: '脆弱',     desc: '获得的格挡减少 25%。',                                        decay: true },
    'STRENGTH':     { name: '力量',     desc: '每层力量使攻击伤害增加 1 点。',                                decay: false },
    'DEXTERITY':    { name: '敏捷',     desc: '每层敏捷使获得的格挡增加 1 点。',                              decay: false },
    'POISON':       { name: '毒',       desc: '回合结束时，造成等同于毒层数的伤害，然后减少 1 层。',           decay: true },
    'REGENERATION': { name: '再生',     desc: '回合结束时，恢复等同于再生层数的生命，然后减少 1 层。',          decay: true },
    'RITUAL':       { name: '仪式',     desc: '回合开始时，获得 1 点力量。',                                  decay: false },
    'EXHAUST':      { name: '消耗',     desc: '打出的牌将被消耗，本场战斗无法再次抽到。',                     decay: false },
    'INTANGIBLE':   { name: '无实体',   desc: '持续一回合，受到的所有伤害降低为 1。',                         decay: true }
  },
  RARITY_COLORS: { 'START': '#888888', 'COMMON': '#95a5a6', 'UNCOMMON': '#3498db', 'RARE': '#f39c12', 'LEGENDARY': '#ff6b6b', 'SPECIAL': '#9b59b6' },
  
  getStatusName(type) { return this.STATUS_INFO[type]?.name || type; },

  getCardEffects(card) {
    if (!card) return [];
    if (Array.isArray(card.effects) && card.effects.length > 0) {
      return card.effects;
    }
    if (card.applyStatusType) {
      return [{ type: card.applyStatusType, count: card.applyStatusCount || 0, target: card.applyStatusTarget || 'ENEMY' }];
    }
    return [];
  },

  getCardEffectText(card) {
    if (!card) return '无效果';
    const parts = [];

    if (card.xCost) parts.push(`X耗能 (消耗所有能量，效果触发X次)`);
    if (card.damage > 0) {
        const dmgText = card.multiHitCount > 1 ? `造成 ${card.damage} 点伤害 ${card.multiHitCount} 次` : `造成 ${card.damage} 点伤害`;
        parts.push(card.aoe ? `AOE: ${dmgText}` : dmgText);
    }
    if (card.block > 0) parts.push(`获得 ${card.block} 点格挡`);
    if (card.selfDamage > 0) parts.push(`失去 ${card.selfDamage} 点生命`);
    if (card.energyGain > 0) parts.push(`获得 ${card.energyGain} 点能量`);

    // 🆕 随机目标描述
    if (card.randomTarget) {
      parts.push('随机攻击敌人');
    }

    // 🆕 力量倍率描述
    if (card.strengthMultiplier && card.strengthMultiplier > 1) {
        parts.push(`力量发挥 ${card.strengthMultiplier} 倍效果`);
    }

    // 🆕 回合结束伤害（灼烧等状态牌）
    if (card.endOfTurnDamage > 0) {
        parts.push(`回合结束时受到 ${card.endOfTurnDamage} 点伤害`);
    }

    // 🆕 抽到时效果（虚空的失去能量等）
    if (card.energyLossOnDraw > 0) {
        parts.push(`抽到时失去 ${card.energyLossOnDraw} 点能量`);
    }

    // 🆕 特殊机制卡牌
    if (card.blockToDamage) parts.push('造成当前格挡值的伤害');
    if (card.exhaustNonAttackBlock > 0) parts.push(`消耗手中所有非攻击牌，每张获 ${card.exhaustNonAttackBlock} 点格挡`);
    if (card.addWoundCount > 0) parts.push(`增加 ${card.addWoundCount} 张伤口到手牌`);
    if (card.blockPerAttack > 0) parts.push(`本回合每打出1张攻击牌获得 ${card.blockPerAttack} 点格挡`);
    if (card.forgeDamageBonus > 0) {
        const cnt = card.forgeCount || 0;
        parts.push(`可多次锻造（已锻 ${cnt} 次，每次 +${card.forgeDamageBonus} 伤害）`);
    }
    if (card.energyGainIfDiscarded > 0) parts.push(`本回合丢弃过牌获得 ${card.energyGainIfDiscarded} 能量`);
    if (card.discardAllForCards) parts.push(`丢弃所有手牌，获得对应数量的小刀`);
    if (card.discardAllForDraw) parts.push(`丢弃所有手牌，抽对应数量的牌`);
    if (card.buffCardName && card.buffDamageAmount > 0) parts.push(`本场战斗${card.buffCardName === 'shiv' ? '小刀' : card.buffCardName}伤害 +${card.buffDamageAmount}`);
    if (card.doublePoison) parts.push(`将目标敌人的中毒翻倍`);
    if (card.poisonAllPerCard > 0) parts.push(`本回合之后每抽1张牌，所有敌人获 ${card.poisonAllPerCard} 层毒`);
    if (card.extraPoisonTick) parts.push(`回合结束时中毒额外结算1次`);
    if (card.addCardId && card.addCardCount > 0) parts.push(`增加 ${card.addCardCount} 张${card.addCardId === 'shiv' ? '小刀' : card.addCardId}到手牌`);
    if (card.requiresEmptyDrawPile) parts.push('抽牌堆为空时才可打出');
    if (card.upgradeAllInHand) parts.push('临时升级手牌中所有牌');
    else if (card.upgradeHandCount > 0) {
        const mode = card.upgradeHandMode === 'SELECT' ? '选择' : '随机';
        parts.push(`${mode}临时升级手牌中 ${card.upgradeHandCount} 张牌`);
    }

    if (card.drawFirst) {
        if (card.drawCount > 0) parts.push(`抽 ${card.drawCount} 张牌`);
        if (card.exhaustHandCount > 0) {
            const mode = card.exhaustHandMode === 'SELECT' ? '选择' : '随机';
            parts.push(`${mode}消耗 ${card.exhaustHandCount} 张手牌`);
        }
        if (card.discardCount > 0) {
            const mode = card.discardMode === 'SELECT' ? '选择' : '随机';
            parts.push(`${mode}丢弃 ${card.discardCount} 张手牌`);
        }
    } else {
        if (card.exhaustHandCount > 0) {
            const mode = card.exhaustHandMode === 'SELECT' ? '选择' : '随机';
            parts.push(`${mode}消耗 ${card.exhaustHandCount} 张手牌`);
        }
        if (card.discardCount > 0) {
            const mode = card.discardMode === 'SELECT' ? '选择' : '随机';
            parts.push(`${mode}丢弃 ${card.discardCount} 张手牌`);
        }
        if (card.drawCount > 0) parts.push(`抽 ${card.drawCount} 张牌`);
    }

    const effects = this.getCardEffects(card);
    effects.forEach(eff => {
      if (!eff.type) return;
      const sn = this.getStatusName(eff.type);
      const isSelf = eff.target === 'SELF';
      const aoeText = card.aoe && !isSelf ? '所有敌人' : (isSelf ? '自身' : '目标');
      parts.push(`给${aoeText}${isSelf ? '获得' : '施加'} ${eff.count} 层${sn}`);
    });

    if (card.unplayable) parts.push('无法被打出');
    if (card.innate) parts.push('固有');
    if (card.exhaust) parts.push('消耗');
    if (card.ethereal) parts.push('虚无');
    if (card.retain) parts.push('保留');
    // 🆕 愤怒效果：弃牌堆增加一张复制品
    if (card.copyToDiscard) {
      parts.push('弃牌堆增加一张复制品');
    }

    return parts.join('，') || '无效果';
  },

  getCardTypeLabel(card) {
    if (card.type === 'ATTACK') return '攻击';
    if (card.type === 'SKILL')  return '技能';
    if (card.type === 'POWER')  return '能力';
    if (card.type === 'STATUS') return '状态';
    return '未知';
  },
  getCardTypeClass(card) {
    if (card.type === 'ATTACK') return 'attack';
    if (card.type === 'SKILL')  return 'skill';
    if (card.type === 'POWER')  return 'power';
    if (card.type === 'STATUS') return 'status';
    return '';
  },
  getCardCharStyle(card) {
    if (!card) return 'linear-gradient(135deg, #667eea, #764ba2)';
    if (card.type === 'STATUS') return 'linear-gradient(135deg, #4a154b, #2d0a2e)';
    const charId = (card.charId && card.charId !== '') ? card.charId : '1';
    if (charId === '0') return 'linear-gradient(135deg, #808080, #A9A9A9)';
    if (charId === '2') return 'linear-gradient(135deg, #2ecc71, #27ae60)';
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
    const effects = this.getCardEffects(card);
    const firstType = effects.length > 0 ? effects[0].type : null;
    this.bindTooltip(el, firstType);
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
