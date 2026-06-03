package com.slaythespire.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 游戏数据仓库 - 从 JSON 配置文件加载卡牌/怪物数据
 * 替代原静态 GameConfig，实现数据与代码完全解耦
 */
@Repository
public class GameDataRepository {

    private static final Logger log = LoggerFactory.getLogger(GameDataRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();

    // 内存缓存：ID -> 模板对象
    private final Map<String, CardTemplate> cardCache = new LinkedHashMap<>();
    private final Map<String, EnemyTemplate> enemyCache = new LinkedHashMap<>();

    /**
     * Spring 容器初始化后自动执行：读取 classpath 下的 JSON 文件
     */
    @PostConstruct
    public void loadData() {
        try {
            loadCards();
            loadEnemies();
            log.info("✅ 游戏配置加载完成 | 卡牌: {} 张 | 怪物: {} 只", cardCache.size(), enemyCache.size());
        } catch (Exception e) {
            log.error("❌ 游戏配置文件加载失败，请检查 resources/config/ 目录", e);
            throw new RuntimeException("Failed to load game config", e);
        }
    }

    private void loadCards() throws IOException {
        try (InputStream is = new ClassPathResource("config/cards.json").getInputStream()) {
            List<CardTemplate> cards = mapper.readValue(is, new TypeReference<List<CardTemplate>>() {});
            for (CardTemplate c : cards) {
                cardCache.put(c.getId(), c);
            }
        }
    }

    private void loadEnemies() throws IOException {
        try (InputStream is = new ClassPathResource("config/enemies.json").getInputStream()) {
            List<EnemyTemplate> enemies = mapper.readValue(is, new TypeReference<List<EnemyTemplate>>() {});
            for (EnemyTemplate e : enemies) {
                enemyCache.put(e.getId(), e);
            }
        }
    }

    // ================= 对外查询接口 =================

    /** 获取所有卡牌（用于初始化牌堆） */
    public List<CardTemplate> getAllCards() {
        return new ArrayList<>(cardCache.values());
    }

    /** 根据 ID 获取指定卡牌 */
    public CardTemplate getCardById(String id) {
        return cardCache.get(id);
    }

    /** 获取所有怪物 */
    public List<EnemyTemplate> getAllEnemies() {
        return new ArrayList<>(enemyCache.values());
    }

    /** 根据 ID 获取指定怪物 */
    public EnemyTemplate getEnemyById(String id) {
        return enemyCache.get(id);
    }
}