package com.slaythespire.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slaythespire.model.SaveData;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class SaveService {

    private final ObjectMapper mapper = new ObjectMapper();
    private String saveDir = "saves/";

    @PostConstruct
    public void init() {
        // 确保存档目录存在
        try {
            Path path = Paths.get(saveDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("✅ 创建存档目录: " + saveDir);
            }
        } catch (IOException e) {
            System.err.println("❌ 创建存档目录失败: " + e.getMessage());
        }
    }

    public void saveGame(String charId, SaveData data) {
        try {
            File file = new File(saveDir + "save_" + charId + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
            System.out.println("✅ 存档已保存: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ 存档失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("存档失败: " + e.getMessage(), e);
        }
    }

    public SaveData loadGame(String charId) {
        File file = new File(saveDir + "save_" + charId + ".json");
        if (!file.exists()) {
            System.out.println("⚠️ 存档不存在: " + file.getAbsolutePath());
            return null;
        }
        try {
            SaveData data = mapper.readValue(file, SaveData.class);
            System.out.println("✅ 读档成功: " + file.getAbsolutePath());
            return data;
        } catch (IOException e) {
            System.err.println("❌ 读档失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void deleteSave(String charId) {
        File file = new File(saveDir + "save_" + charId + ".json");
        if (file.exists()) {
            file.delete();
            System.out.println("✅ 存档已删除: " + file.getAbsolutePath());
        }
    }
}