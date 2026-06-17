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
    // 🆕 统一全局存档文件名
    private String saveFileName = "save.json";

    @PostConstruct
    public void init() {
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

    // 🆕 移除 charId 参数
    public void saveGame(SaveData data) {
        try {
            File file = new File(saveDir + saveFileName);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
            System.out.println("✅ 全局存档已保存: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ 存档失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("存档失败: " + e.getMessage(), e);
        }
    }

    // 🆕 移除 charId 参数
    public SaveData loadGame() {
        File file = new File(saveDir + saveFileName);
        if (!file.exists()) {
            System.out.println("⚠️ 全局存档不存在: " + file.getAbsolutePath());
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

    // 🆕 移除 charId 参数
    public void deleteSave() {
        File file = new File(saveDir + saveFileName);
        if (file.exists()) {
            file.delete();
            System.out.println("✅ 全局存档已删除: " + file.getAbsolutePath());
        }
    }
}