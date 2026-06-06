package com.slaythespire.game.model.factory;

import com.slaythespire.game.model.GameStatus;
import com.slaythespire.game.model.StatusEffect;
import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.StatusTemplate;

public class StatusFactory {
    // ✅ 必须是 3 个参数
    public static StatusEffect create(String typeId, int count, GameDataRepository repo) {
        if (typeId == null || repo == null) return null;
        StatusTemplate template = repo.getStatusById(typeId);
        return template != null ? new GameStatus(template, count) : null;
    }
}