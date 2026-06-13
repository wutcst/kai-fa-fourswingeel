package com.slaythespire.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Repository
public class GameDataRepository {
    private List<CardTemplate> cards = new ArrayList<>();
    private List<EnemyTemplate> enemies = new ArrayList<>();
    private List<RelicTemplate> relics = new ArrayList<>();
    private List<StatusTemplate> statuses = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadJson("config/cards.json", cards, new TypeReference<List<CardTemplate>>() {});
        loadJson("config/enemies.json", enemies, new TypeReference<List<EnemyTemplate>>() {});
        loadJson("config/relics.json", relics, new TypeReference<List<RelicTemplate>>() {});
        loadJson("config/statuses.json", statuses, new TypeReference<List<StatusTemplate>>() {});
    }

    private <T> void loadJson(String path, List<T> target, TypeReference<List<T>> typeRef) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource(path).getInputStream();
            List<T> data = mapper.readValue(is, typeRef);
            target.clear();
            target.addAll(data);
            System.out.println("✅ 加载配置成功: " + path + " (" + data.size() + " 条)");
        } catch (IOException e) {
            System.err.println("❌ 加载配置失败: " + path + " - " + e.getMessage());
        }
    }

    public List<CardTemplate> getAllCards() { return cards; }
    public List<EnemyTemplate> getAllEnemies() { return enemies; }
    public List<RelicTemplate> getAllRelics() { return relics; }
    public List<StatusTemplate> getAllStatuses() { return statuses; }

    public StatusTemplate getStatusById(String id) {
        return statuses.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    }
    public RelicTemplate getRelicById(String id) {
        return relics.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }
    
    public CardTemplate getCardById(String id) {
        return cards.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }

    // ✅ 新增：根据名称查找卡牌模板（用于存档旧卡牌补全 charId）
    public CardTemplate getCardByName(String name) {
        return cards.stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
    }
}
