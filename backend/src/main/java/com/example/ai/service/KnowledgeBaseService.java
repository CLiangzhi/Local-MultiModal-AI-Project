package com.example.ai.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    // 核心约束：统一的知识库根目录
    private final Path rootLocation = Paths.get("local_data/knowledge_base");

    public KnowledgeBaseService() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("无法初始化知识库存储目录", e);
        }
    }

    // 存储文件到对应用户的专属文件夹下
    public void saveUserFile(String userId, MultipartFile file) throws IOException {
        Path userDir = rootLocation.resolve(userId);
        if (!Files.exists(userDir)) {
            Files.createDirectories(userDir); // 约束：同一个登录者只有一个文件夹
        }
        String filename = file.getOriginalFilename();
        if (filename == null) return;

        Files.copy(file.getInputStream(), userDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("用户 " + userId + " 的资产已安全存储至: " + userDir.resolve(filename));
    }

    // 列出用户的专属文件列表
    public List<String> listUserFiles(String userId) {
        File folder = rootLocation.resolve(userId).toFile();
        if (!folder.exists() || !folder.isDirectory()) {
            return Collections.emptyList();
        }
        return Arrays.stream(folder.listFiles())
                .filter(File::isFile)
                .map(File::getName)
                .collect(Collectors.toList());
    }
}