//package com.example.ai.controller;
//
//import com.example.ai.service.OllamaService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.MediaType;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//import reactor.core.publisher.Flux;
//import java.io.*;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Base64;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.util.stream.Collectors;
//import java.util.Comparator;
//import reactor.core.scheduler.Schedulers;
//import com.example.ai.entity.ChatMessageEntity;
//import com.example.ai.repository.ChatMessageRepository;
//import com.example.ai.service.KnowledgeBaseService;
//import java.util.ArrayList;
//import com.example.ai.service.RagService;
//@RestController
//@RequestMapping("/api")
//@CrossOrigin(origins = "*")
//public class ChatController {
//
//    @Autowired
//    private OllamaService ollamaService;
//    @Autowired
//    private ChatMessageRepository chatMessageRepository;
//
//    @Autowired
//    private KnowledgeBaseService knowledgeBaseService;
//
//    @Autowired
//    private RagService ragService;
//
//    // 模拟当前登录的商业客户ID (未来将由 JWT Token 拦截器提供)
//    private final String getCurrentUserId() = "user_test_001";
//    // 1. 获取当前用户的历史记录 (Module B)
//    @GetMapping("/history")
//    public List<ChatMessageEntity> getHistory() {
//        return chatMessageRepository.findByUserIdOrderByIdAsc(getCurrentUserId());
//    }
//
//    // 2. 获取当前用户的知识库文件列表 (Module C)
//    @GetMapping("/knowledge/files")
//    public List<String> getKnowledgeFiles() {
//        return knowledgeBaseService.listUserFiles(getCurrentUserId());
//    }
//
//    // 3. 上传文件到私有知识库 (Module C)
//    @PostMapping("/knowledge/upload")
//    public String uploadKnowledgeFile(@RequestParam("file") MultipartFile file) {
//        try {
//            knowledgeBaseService.saveUserFile(getCurrentUserId(), file);
//            // 👉 新增：物理存储完成后，立即触发后台向量化
//            Path savedFilePath = Paths.get("local_data/knowledge_base", getCurrentUserId(), file.getOriginalFilename());
//            // 异步执行，防止大文件导致前端上传请求超时
//            new Thread(() -> ragService.ingestDocumentForUser(getCurrentUserId(), savedFilePath)).start();
//
//            return "文件上传成功，正在后台构建专属知识索引...";
//        } catch (Exception e) {
//            return "文件存储失败: " + e.getMessage();
//        }
//    }
//
//    // 4. 重构纯文本流式对话 (Module B 持久化支持)
//    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> textChat(@RequestBody Map<String, Object> payload) {
//        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
//
//        // 获取用户的最后一条消息并存入数据库
//        Map<String, Object> lastMsg = messages.get(messages.size() - 1);
////        saveMessageToDb("user", lastMsg.get("content").toString());
//        String userQuery = lastMsg.get("content").toString();
//
//        saveMessageToDb("user", userQuery);
//        // 👉 新增：在发给大模型之前，先去该用户的专属知识库里“捞”点资料
//        String retrievedContext = ragService.retrieveContext(getCurrentUserId(), userQuery);
//
//        if (!retrievedContext.isEmpty()) {
//            // 如果捞到了资料，我们要“暗中”修改最后一次发给 Qwen 的 Prompt
//            System.out.println("[RAG] 命中本地知识库，已注入上下文");
//            String enhancedPrompt = String.format(
//                    "请基于以下我提供的内部资料来回答问题。如果资料中没有相关信息，请根据你的知识正常回答。\n\n【内部资料】：\n%s\n\n【我的问题】：%s",
//                    retrievedContext, userQuery
//            );
//            // 替换原来单纯的提问
//            lastMsg.put("content", enhancedPrompt);
//        }
//        // 收集大模型的流式回答以便最后统一存入数据库
//        StringBuilder assistantFullResponse = new StringBuilder();
//
//        return ollamaService.chatStream("qwen2.5:7b", messages)
//                .filter(chunk -> chunk != null && !chunk.isEmpty())
//                .doOnNext(assistantFullResponse::append) // 实时拼接内容
//                .doOnComplete(() -> {
//                    // 流式输出结束后，将完整的大模型回答存入数据库
//                    saveMessageToDb("assistant", assistantFullResponse.toString());
//                });
//    }
//
//    // 保存消息到数据库的私有辅助方法
//    private void saveMessageToDb(String role, String content) {
//        ChatMessageEntity entity = new ChatMessageEntity();
//        entity.setUserId(getCurrentUserId());
//        entity.setRole(role);
//        entity.setContent(content);
//        chatMessageRepository.save(entity);
//    }
////    // 基础文本对话（真正的流式输出）
////    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
////    public Flux<String> textChat(@RequestBody Map<String, Object> payload) {
////        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
////
////        // 直接返回 Flux，Spring WebFlux 会自动将其转换为 SSE 流发送给前端
////        return ollamaService.chatStream("qwen2.5:7b", messages)
////                // 过滤掉可能出现的空字符，防止前端解析异常
////                .filter(chunk -> chunk != null && !chunk.isEmpty());
////    }
//
//        // 商业级重构：多模态处理核心接口（异步流式 + 进度反馈）
//    @PostMapping(value = "/chat/media", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> mediaChat(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam("type") String type,
//            @RequestParam(value = "prompt", defaultValue = "请描述这个文件") String prompt) throws Exception {
//
//        // 必须在 Spring 的主线程中先读取文件字节，否则进入异步线程后 MultipartFile 可能会失效
//        byte[] fileBytes = file.getBytes();
//        String originalFilename = file.getOriginalFilename();
//
//        // 使用 Flux.create 创建一个自定义的异步数据流
//        return Flux.<String>create(sink -> {
//            Path tempDir = null;
//            try {
//                // 【状态 1：文件就绪】
//                sink.next("[系统] 接收到文件，正在分配本地计算资源...\n");
//
//                tempDir = Files.createTempDirectory("ai_media_");
//                File inputFile = new File(tempDir.toFile(), UUID.randomUUID() + "_" + originalFilename);
//                Files.write(inputFile.toPath(), fileBytes);
//
//                if ("image".equals(type)) {
//                 sink.next("[系统] 开始调用 LLaVA 解析图像画面...\n");
//                    String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(inputFile.toPath()));
//                    String result = ollamaService.analyzeImage(base64, prompt);
//                    sink.next(result);
//                    sink.complete(); // 图像处理完成，关闭流
//
//                } else if ("audio".equals(type)) {
//                    sink.next("[系统] 正在唤醒 Whisper 处理音频转录...\n");
//                    String transcript = executeWhisper(inputFile, tempDir);
//
//                    sink.next("[系统] 转录完成！提取内容：\n\"" + transcript + "\"\n\n[系统] 正在呼叫大模型分析思考...\n");
//                    String finalPrompt = "用户上传了语音，识别内容为：\"" + transcript + "\"。请根据内容回答问题：" + prompt;
//
//                    // 【关键技巧】桥接 Ollama 的流式响应到当前流
//                    ollamaService.chatStream("qwen2.5:7b", List.of(Map.of("role", "user", "content", finalPrompt)))
//                            .subscribe(chunk -> sink.next(chunk),
//                                    error -> sink.error(error),
//                                    () -> sink.complete());
//
//                } else if ("video".equals(type)) {
//                    sink.next("[系统] 启动媒体处理引擎 (FFmpeg)...\n");
//
//                    // 1. 抽帧
//                    File frame = new File(tempDir.toFile(), "frame.jpg");
//                    sink.next("[系统] 正在抽取视频关键帧...\n");
//                    executeCommand("ffmpeg -y -i " + inputFile.getAbsolutePath() + " -ss 00:00:01 -vframes 1 " + frame.getAbsolutePath());
//
//                    // 2. 提音
//                    File audio = new File(tempDir.toFile(), "audio.wav");
//                    sink.next("[系统] 正在分离视频音轨...\n");
//                    executeCommand("ffmpeg -y -i " + inputFile.getAbsolutePath() + " -vn -acodec pcm_s16le -ar 16000 -ac 1 " + audio.getAbsolutePath());
//
//                    // 3. 多模态并发处理 (此处暂用同步简化，未来可优化为 CompletableFuture)
//                    sink.next("[系统] 正在启动 Whisper 语音识别 与 LLaVA 图像分析...\n");
//                    String transcript = executeWhisper(audio, tempDir);
//
//                    String base64Frame = Base64.getEncoder().encodeToString(Files.readAllBytes(frame.toPath()));
//                    String visionDesc = ollamaService.analyzeImage(base64Frame, "请尽可能详细地描述这幅画面中的所有细节。");
//
//                    // 4. 汇总给 Qwen
//                    sink.next("[系统] 多模态数据整合完毕，大模型开始生成最终分析...\n\n");
//                    String finalPrompt = String.format("这是一份视频分析报告。\n【画面视觉内容】：%s\n【提取出的语音内容】：%s\n\n请结合以上两部分信息，回答用户的问题：%s",
//                           visionDesc, transcript, prompt);
//
//                    ollamaService.chatStream("qwen2.5:7b", List.of(Map.of("role", "user", "content", finalPrompt)))
//                            .subscribe(chunk -> sink.next(chunk),
//                                    error -> sink.error(error),
//                                    () -> sink.complete());
//                } else {
//                    sink.next("不支持的文件类型");
//                    sink.complete();
//                }
//
//            } catch (Exception e) {
//                sink.next("\n[系统异常] 媒体处理失败: " + e.getMessage());
//                sink.complete();
//            } finally {
//                // 【防御性编程】阅后即焚，绝不在服务器留下垃圾文件
//                cleanupTempDir(tempDir);
//            }
//            // 将整个阻塞的媒体处理过程，丢入 Spring 的弹性后台线程池，防止卡死 Web 服务器
//        }).subscribeOn(Schedulers.boundedElastic());
//    }
//
//    // ================== 底层引擎重构 ==================
//
//    private String executeWhisper(File audioFile, Path outputDir) throws Exception {
//        String command = String.format("whisper %s --model small --language Chinese --output_format txt --output_dir %s",
//                audioFile.getAbsolutePath(), outputDir.toAbsolutePath());
//        executeCommand(command);
//
//        File resultTxt = new File(audioFile.getAbsolutePath().replaceAll("\\.[^.]+$", "") + ".txt");
//        if (resultTxt.exists()) {
//            return Files.readString(resultTxt.toPath());
//        }
//        return "[语音识别未生成有效文字]";
//    }
//
//    // 跨平台命令行执行器
//    private void executeCommand(String command) throws Exception {
//        System.out.println("[Media Engine] Executing: " + command);
//
//        // 【核心修复】自动识别操作系统，避免在 Linux/Mac 上报错
//        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
//        ProcessBuilder pb;
//        if (isWindows) {
//            pb = new ProcessBuilder("cmd.exe", "/c", command);
//        } else {
//            pb = new ProcessBuilder("sh", "-c", command);
//        }
//
//        pb.redirectErrorStream(true);
//        Process process = pb.start();
//
//        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), isWindows ? "GBK" : "UTF-8"))) {
//            String line;
//            while ((line = reader.readLine()) != null) {
//                // 可以注释掉下面这行，以减少控制台的垃圾日志
//                // System.out.println("  [CLI]: " + line);
//            }
//        }
//        int exitCode = process.waitFor();
//        if (exitCode != 0) {
//            throw new RuntimeException("底层引擎执行失败，Exit Code: " + exitCode);
//        }
//    }
//
//    // 临时文件粉碎机
//    private void cleanupTempDir(Path tempDir) {
//        if (tempDir == null) return;
//        try {
//            Files.walk(tempDir)
//                    .sorted(Comparator.reverseOrder())
//                    .map(Path::toFile)
//                    .forEach(File::delete);
//            System.out.println("[Security] 已成功清理临时工作区: " + tempDir);
//        } catch (Exception e) {
//            System.err.println("[警告] 临时工作区清理失败: " + e.getMessage());
//        }
//    }
//}













//package com.example.ai.controller;
//
//import com.example.ai.entity.ChatMessageEntity;
//import com.example.ai.repository.ChatMessageRepository;
//import com.example.ai.service.KnowledgeBaseService;
//import com.example.ai.service.OllamaService;
//import com.example.ai.service.RagService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//import org.springframework.security.core.context.SecurityContextHolder;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.InputStreamReader;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Base64;
//import java.util.Comparator;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//import java.util.stream.Collectors;
//
//
//@RestController
//@RequestMapping("/api")
//@CrossOrigin(origins = "*")
//public class ChatController {
//
//    @Autowired
//    private OllamaService ollamaService;
//
//    @Autowired
//    private ChatMessageRepository chatMessageRepository;
//
//    @Autowired
//    private KnowledgeBaseService knowledgeBaseService;
//
//    @Autowired
//    private RagService ragService;
//
//    private String getCurrentUserId() {
//        return SecurityContextHolder.getContext().getAuthentication().getName();
//    }
//
//    // ================== 历史记录与知识库管理接口 ==================
//
//    @GetMapping("/history")
//    public List<ChatMessageEntity> getHistory() {
//        return chatMessageRepository.findByUserIdOrderByIdAsc(getCurrentUserId());
//    }
//
//    @GetMapping("/knowledge/files")
//    public List<String> getKnowledgeFiles() {
//        return knowledgeBaseService.listUserFiles(getCurrentUserId());
//    }
//
////    @PostMapping("/knowledge/upload")
////    public String uploadKnowledgeFile(@RequestParam("file") MultipartFile file) {
////        try {
////            knowledgeBaseService.saveUserFile(getCurrentUserId(), file);
////            Path savedFilePath = Paths.get("local_data/knowledge_base", getCurrentUserId(), file.getOriginalFilename());
////            // 异步向量化
////            new Thread(() -> ragService.ingestDocumentForUser(getCurrentUserId(), savedFilePath)).start();
////            return "文件上传成功，正在后台构建专属知识索引...";
////        } catch (Exception e) {
////            return "文件存储失败: " + e.getMessage();
////        }
////    }
//@PostMapping("/knowledge/upload")
//public String uploadKnowledgeFile(@RequestParam("file") MultipartFile file) {
//    try {
//        // 【关键修复】：在开启子线程之前（还在主线程时），先把 userId 拿出来！
//        final String currentUserId = getCurrentUserId();
//
//        knowledgeBaseService.saveUserFile(currentUserId, file);
//        Path savedFilePath = Paths.get("local_data/knowledge_base", currentUserId, file.getOriginalFilename());
//
//        // 异步向量化：直接把拿到的 currentUserId 字符串传进去，不要在里面再调 SecurityContext 了
//        new Thread(() -> ragService.ingestDocumentForUser(currentUserId, savedFilePath)).start();
//
//        return "文件上传成功，正在后台构建专属知识索引...";
//    } catch (Exception e) {
//        return "文件存储失败: " + e.getMessage();
//    }
//}
//
//    private void saveMessageToDb(String role, String content) {
//        ChatMessageEntity entity = new ChatMessageEntity();
//        entity.setUserId(getCurrentUserId());
//        entity.setRole(role);
//        entity.setContent(content);
//        chatMessageRepository.save(entity);
//    }
//
//    // ================== 核心对话接口（恢复为你喜欢的非流式，保留 RAG 和 DB） ==================
//
//    @PostMapping(value = "/chat")
//    public String textChat(@RequestBody Map<String, Object> payload) {
//        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
//        Map<String, Object> lastMsg = messages.get(messages.size() - 1);
//        String userQuery = lastMsg.get("content").toString();
//
//        // 1. 存入数据库
//        saveMessageToDb("user", userQuery);
//
//        // 2. RAG 知识库检索增强
//        String retrievedContext = ragService.retrieveContext(getCurrentUserId(), userQuery);
//        if (!retrievedContext.isEmpty()) {
//            System.out.println("[RAG] 命中本地知识库，已注入上下文");
//            String enhancedPrompt = String.format(
//                    "请基于以下我提供的内部资料来回答问题。如果资料中没有相关信息，请根据你的知识正常回答。\n\n【内部资料】：\n%s\n\n【我的问题】：%s",
//                    retrievedContext, userQuery
//            );
//            lastMsg.put("content", enhancedPrompt);
//        }
//
//        // 3. 同步等待模型回答（一口气返回，保证排版整洁）
//        String response = ollamaService.chatStream("qwen2.5:7b", messages)
//                .collectList().block().stream().collect(Collectors.joining());
//
//        // 4. 存入数据库
//        saveMessageToDb("assistant", response);
//
//        return response;
//    }
//
//    @PostMapping(value = "/chat/media")
//    public String mediaChat(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam("type") String type,
//            @RequestParam(value = "prompt", defaultValue = "请描述这个文件") String prompt) throws Exception {
//
//        Path tempDir = Files.createTempDirectory("ai_media_");
//        File inputFile = new File(tempDir.toFile(), UUID.randomUUID() + "_" + file.getOriginalFilename());
//        file.transferTo(inputFile);
//
//        String finalResponse = "处理失败或不支持的类型";
//
//        try {
//            saveMessageToDb("user", "[上传了媒体文件] " + prompt);
//
//            if ("image".equals(type)) {
//                String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(inputFile.toPath()));
//                finalResponse = ollamaService.analyzeImage(base64, prompt);
//
//            } else if ("audio".equals(type)) {
//                String transcript = executeWhisper(inputFile, tempDir);
//                String finalPrompt = "用户上传了语音，识别内容为：\"" + transcript + "\"。请根据内容回答问题：" + prompt;
//                finalResponse = ollamaService.chatStream("qwen2.5:7b", List.of(Map.of("role", "user", "content", finalPrompt)))
//                        .collectList().block().stream().collect(Collectors.joining());
//
//            } else if ("video".equals(type)) {
//                File frame = new File(tempDir.toFile(), "frame.jpg");
//                executeCommand("ffmpeg -y -i " + inputFile.getAbsolutePath() + " -ss 00:00:01 -vframes 1 " + frame.getAbsolutePath());
//
//                File audio = new File(tempDir.toFile(), "audio.wav");
//                executeCommand("ffmpeg -y -i " + inputFile.getAbsolutePath() + " -vn -acodec pcm_s16le -ar 16000 -ac 1 " + audio.getAbsolutePath());
//
//                String transcript = executeWhisper(audio, tempDir);
//                String base64Frame = Base64.getEncoder().encodeToString(Files.readAllBytes(frame.toPath()));
//                String visionDesc = ollamaService.analyzeImage(base64Frame, "请描述视频画面内容");
//
//                String finalPrompt = String.format("视频分析报告：\n画面内容：%s\n语音内容：%s\n请结合以上信息回答用户问题：%s",
//                        visionDesc, transcript, prompt);
//                finalResponse = ollamaService.chatStream("qwen2.5:7b", List.of(Map.of("role", "user", "content", finalPrompt)))
//                        .collectList().block().stream().collect(Collectors.joining());
//            }
//        } finally {
//            // 保留阅后即焚，防止磁盘爆炸
//            cleanupTempDir(tempDir);
//        }
//
//        saveMessageToDb("assistant", finalResponse);
//        return finalResponse;
//    }
//
//    // ================== 底层命令行工具 ==================
//
//    private String executeWhisper(File audioFile, Path outputDir) throws Exception {
//        String command = String.format("whisper %s --model small --language Chinese --output_format txt --output_dir %s",
//                audioFile.getAbsolutePath(), outputDir.toAbsolutePath());
//        executeCommand(command);
//
//        File resultTxt = new File(audioFile.getAbsolutePath().replaceAll("\\.[^.]+$", "") + ".txt");
//        if (resultTxt.exists()) {
//            return Files.readString(resultTxt.toPath());
//        }
//        return "[语音识别失败]";
//    }
//
//    private void executeCommand(String command) throws Exception {
//        System.out.println("正在执行命令: " + command);
//        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
//        ProcessBuilder pb = isWindows ? new ProcessBuilder("cmd.exe", "/c", command) : new ProcessBuilder("sh", "-c", command);
//        pb.redirectErrorStream(true);
//        Process process = pb.start();
//
//        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), isWindows ? "GBK" : "UTF-8"))) {
//            String line;
//            while ((line = reader.readLine()) != null) {
//                System.out.println("  [CLI LOG]: " + line);
//            }
//        }
//        int exitCode = process.waitFor();
//        if (exitCode != 0) {
//            System.err.println("命令执行失败，退出码: " + exitCode);
//        }
//    }
//
//    private void cleanupTempDir(Path tempDir) {
//        if (tempDir == null) return;
//        try {
//            Files.walk(tempDir)
//                    .sorted(Comparator.reverseOrder())
//                    .map(Path::toFile)
//                    .forEach(File::delete);
//            System.out.println("[Security] 已成功清理临时工作区: " + tempDir);
//        } catch (Exception e) {
//            System.err.println("[警告] 临时工作区清理失败: " + e.getMessage());
//        }
//    }
//}














package com.example.ai.controller;

import com.example.ai.entity.ChatMessageEntity;
import com.example.ai.repository.ChatMessageRepository;
import com.example.ai.service.KnowledgeBaseService;
import com.example.ai.service.OllamaService;
import com.example.ai.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private OllamaService ollamaService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private RagService ragService;

    // 获取当前登录用户
    private String getCurrentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // ================== 历史记录与知识库管理 ==================

    @GetMapping("/history")
    public List<ChatMessageEntity> getHistory() {
        return chatMessageRepository.findByUserIdOrderByIdAsc(getCurrentUserId());
    }

    @GetMapping("/knowledge/files")
    public List<String> getKnowledgeFiles() {
        return knowledgeBaseService.listUserFiles(getCurrentUserId());
    }

    @PostMapping("/knowledge/upload")
    public String uploadKnowledgeFile(@RequestParam("file") MultipartFile file) {
        try {
            // 提前获取 userId，防止异步线程丢失上下文
            final String currentUserId = getCurrentUserId();
            knowledgeBaseService.saveUserFile(currentUserId, file);
            Path savedFilePath = Paths.get("local_data/knowledge_base", currentUserId, file.getOriginalFilename());

            new Thread(() -> ragService.ingestDocumentForUser(currentUserId, savedFilePath)).start();
            return "文件上传成功，正在后台构建专属知识索引...";
        } catch (Exception e) {
            return "文件存储失败: " + e.getMessage();
        }
    }

    // 注意：改成了需要传入 userId 参数，防止在异步流中拿不到
    private void saveMessageToDb(String userId, String role, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setUserId(userId);
        entity.setRole(role);
        entity.setContent(content);
        chatMessageRepository.save(entity);
    }

    // ================== 核心对话接口（恢复流式，保留 RAG 和 DB） ==================

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> textChat(@RequestBody Map<String, Object> payload) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        Map<String, Object> lastMsg = messages.get(messages.size() - 1);
        String userQuery = lastMsg.get("content").toString();

        // 1. 提前提取 userId（极其关键！）
        final String currentUserId = getCurrentUserId();

        // 2. 保存用户提问
        saveMessageToDb(currentUserId, "user", userQuery);

        // 3. RAG 知识库增强
        String retrievedContext = ragService.retrieveContext(currentUserId, userQuery);
        if (!retrievedContext.isEmpty()) {
            System.out.println("[RAG] 命中本地知识库，已注入上下文");
            String enhancedPrompt = String.format(
                    "请基于以下我提供的内部资料来回答问题。如果资料中没有相关信息，请根据你的知识正常回答。\n\n【内部资料】：\n%s\n\n【我的问题】：%s",
                    retrievedContext, userQuery
            );
            lastMsg.put("content", enhancedPrompt);
        }

        // 4. 流式返回，并在流结束时保存完整数据到数据库
        StringBuilder fullResponse = new StringBuilder();
        return ollamaService.chatStream("qwen2.5:7b", messages)
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(fullResponse::append) // 实时拼接流数据
                .doOnComplete(() -> saveMessageToDb(currentUserId, "assistant", fullResponse.toString())); // 结束后存库
    }

    @PostMapping(value = "/chat/media", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> mediaChat(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @RequestParam(value = "prompt", defaultValue = "请描述这个文件") String prompt) throws Exception {

        byte[] fileBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();

        // 提前获取 userId
        final String currentUserId = getCurrentUserId();
        saveMessageToDb(currentUserId, "user", "[上传了媒体文件] " + prompt);

        return Flux.<String>create(sink -> {
            Path tempDir = null;
            StringBuilder fullResponse = new StringBuilder(); // 用于存入数据库的完整回答

            try {
                sink.next("[系统] 接收到文件，正在分配本地计算资源...\n");
                tempDir = Files.createTempDirectory("ai_media_");
                File inputFile = new File(tempDir.toFile(), UUID.randomUUID() + "_" + originalFilename);
                Files.write(inputFile.toPath(), fileBytes);

                if ("image".equals(type)) {
                    sink.next("[系统] 开始调用 LLaVA 解析图像画面...\n");
                    String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(inputFile.toPath()));
                    String result = ollamaService.analyzeImage(base64, prompt);
                    sink.next(result);
                    fullResponse.append("[图片解析完毕]\n").append(result);
                    sink.complete();

                } else if ("audio".equals(type)) {
                    sink.next("[系统] 正在唤醒 Whisper 处理音频转录...\n");
                    String transcript = executeWhisper(inputFile, tempDir);
                    sink.next("[系统] 转录完成！提取内容：\n\"" + transcript + "\"\n\n[系统] 正在呼叫大模型分析思考...\n");

                    String finalPrompt = "用户上传了语音，识别内容为：\"" + transcript + "\"。请根据内容回答问题：" + prompt;
                    ollamaService.chatStream("qwen2.5:7b", List.of(Map.of("role", "user", "content", finalPrompt)))
                            .subscribe(
                                    chunk -> { sink.next(chunk); fullResponse.append(chunk); },
                                    error -> sink.error(error),
                                    () -> sink.complete()
                            );

                } else if ("video".equals(type)) {
                    sink.next("[系统] 启动媒体处理引擎 (FFmpeg)...\n");
                    File frame = new File(tempDir.toFile(), "frame.jpg");
                    executeCommand("ffmpeg -y -i " + inputFile.getAbsolutePath() + " -ss 00:00:01 -vframes 1 " + frame.getAbsolutePath());

                    File audio = new File(tempDir.toFile(), "audio.wav");
                    executeCommand("ffmpeg -y -i " + inputFile.getAbsolutePath() + " -vn -acodec pcm_s16le -ar 16000 -ac 1 " + audio.getAbsolutePath());

                    sink.next("[系统] 正在启动 Whisper 语音识别 与 LLaVA 图像分析...\n");
                    String transcript = executeWhisper(audio, tempDir);
                    String base64Frame = Base64.getEncoder().encodeToString(Files.readAllBytes(frame.toPath()));
                    String visionDesc = ollamaService.analyzeImage(base64Frame, "请尽可能详细地描述画面细节。");

                    sink.next("[系统] 多模态数据整合完毕，大模型开始生成最终分析...\n\n");
                    String finalPrompt = String.format("视频分析报告：\n画面内容：%s\n语音内容：%s\n请结合以上信息回答问题：%s", visionDesc, transcript, prompt);

                    ollamaService.chatStream("qwen2.5:7b", List.of(Map.of("role", "user", "content", finalPrompt)))
                            .subscribe(
                                    chunk -> { sink.next(chunk); fullResponse.append(chunk); },
                                    error -> sink.error(error),
                                    () -> sink.complete()
                            );
                } else {
                    sink.next("不支持的文件类型");
                    sink.complete();
                }

            } catch (Exception e) {
                String errorMsg = "\n[系统异常] 媒体处理失败: " + e.getMessage();
                sink.next(errorMsg);
                fullResponse.append(errorMsg);
                sink.complete();
            } finally {
                cleanupTempDir(tempDir);
                // 流完全结束后，持久化到数据库
                saveMessageToDb(currentUserId, "assistant", fullResponse.toString());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ================== 底层命令行工具 ==================

    private String executeWhisper(File audioFile, Path outputDir) throws Exception {
        String command = String.format("whisper %s --model small --language Chinese --output_format txt --output_dir %s",
                audioFile.getAbsolutePath(), outputDir.toAbsolutePath());
        executeCommand(command);

        File resultTxt = new File(audioFile.getAbsolutePath().replaceAll("\\.[^.]+$", "") + ".txt");
        if (resultTxt.exists()) {
            return Files.readString(resultTxt.toPath());
        }
        return "[语音识别失败]";
    }

    private void executeCommand(String command) throws Exception {
        System.out.println("正在执行命令: " + command);
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        ProcessBuilder pb = isWindows ? new ProcessBuilder("cmd.exe", "/c", command) : new ProcessBuilder("sh", "-c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), isWindows ? "GBK" : "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // System.out.println("  [CLI LOG]: " + line);
            }
        }
        process.waitFor();
    }

    private void cleanupTempDir(Path tempDir) {
        if (tempDir == null) return;
        try {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            System.out.println("[Security] 已成功清理临时工作区: " + tempDir);
        } catch (Exception e) {
            System.err.println("[警告] 临时工作区清理失败: " + e.getMessage());
        }
    }
}