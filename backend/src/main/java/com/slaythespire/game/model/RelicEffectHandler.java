package com.slaythespire.game.model;

import com.slaythespire.game.model.factory.StatusFactory;

/**
 * 遗物效果集中处理器 — 所有遗物逻辑统一放在这里
 * 加新遗物只需在此文件加一个 case，无需改其他代码
 */
public class RelicEffectHandler {

    /** 🪶 凤凰之羽：整局游戏仅触发一次的全局标记 */
    private static boolean deathSaveUsedInRun = false;

    public static boolean isDeathSaveUsedInRun() { return deathSaveUsedInRun; }
    public static void markDeathSaveUsedInRun() { deathSaveUsedInRun = true; }
    public static void resetDeathSaveInRun() { deathSaveUsedInRun = false; }

    /** 玩家回合开始时触发 */
    public static void onPlayerTurnStart(Player player) {
        for (Relic r : player.getRelics()) {
            if (!(r instanceof GameRelic)) continue;
            GameRelic gr = (GameRelic) r;
            switch (gr.getEffectType()) {
                case "HEAL_START_TURN" -> player.heal(gr.getValue());
                case "BLOCK_START_TURN" -> player.gainBlock(gr.getValue());
                case "STRENGTH_IF_HALF_HP" -> {
                    if (player.getHp() * 2 < player.getMaxHp()) {
                        StatusEffect strength = StatusFactory.create("STRENGTH", gr.getValue(), player.getDataRepo());
                        if (strength != null) {
                            player.addStatus(strength);
                            player.addTurnStartLog("🩸 带血匕首触发，获得 " + gr.getValue() + " 层力量");
                        }
                    }
                }
                // 🆕 Boss 遗物：无限之石 — 每回合开始获得 1 层敏捷
                case "DEXTERITY_PER_TURN" -> {
                    StatusEffect dex = StatusFactory.create("DEXTERITY", gr.getValue(), player.getDataRepo());
                    if (dex != null) {
                        player.addStatus(dex);
                        player.addTurnStartLog("🪨 无限之石触发，获得 " + gr.getValue() + " 层敏捷");
                    }
                }
            }
        }
    }

    /** 玩家回合结束时触发 */
    public static void onPlayerTurnEnd(Player player) {
        for (Relic r : player.getRelics()) {
            if (!(r instanceof GameRelic)) continue;
            GameRelic gr = (GameRelic) r;
            switch (gr.getEffectType()) {
                case "HEAL_END_TURN" -> player.heal(gr.getValue());
                case "BLOCK_END_TURN" -> player.gainBlock(gr.getValue());
            }
        }
    }

    /** 检查是否有指定效果的遗物 */
    public static boolean hasEffect(Combatant combatant, String effectType) {
        for (Relic r : combatant.getRelics()) {
            if (r instanceof GameRelic && effectType.equals(((GameRelic) r).getEffectType()))
                return true;
        }
        return false;
    }

    /** 获取指定效果的遗物参数值（只取第一个匹配的） */
    public static int getEffectValue(Combatant combatant, String effectType) {
        for (Relic r : combatant.getRelics()) {
            if (r instanceof GameRelic && effectType.equals(((GameRelic) r).getEffectType()))
                return ((GameRelic) r).getValue();
        }
        return 0;
    }

    /** 荆棘甲反伤 — 对攻击者造成固定伤害 */
    public static void handleThornsDamage(Combatant attacker, Combatant defender) {
        for (Relic r : defender.getRelics()) {
            if (r instanceof GameRelic && "THORNS".equals(((GameRelic) r).getEffectType())) {
                int dmg = ((GameRelic) r).getValue();
                attacker.takeDamage(dmg, null, true);
                break;
            }
        }
    }

    /** 获取每回合伤害上限（魔法护盾） */
    public static int getDamageCapPerTurn(Combatant combatant) {
        for (Relic r : combatant.getRelics()) {
            if (r instanceof GameRelic) {
                GameRelic gr = (GameRelic) r;
                if ("DAMAGE_CAP_PER_TURN".equals(gr.getEffectType()))
                    return gr.getValue();
            }
        }
        return -1;
    }
}
