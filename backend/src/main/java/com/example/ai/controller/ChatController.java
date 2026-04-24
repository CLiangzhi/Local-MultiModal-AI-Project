//package com.example.ai.controller;
//
//import com.example.ai.service.OllamaService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.MediaType;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//import reactor.core.publisher.Flux;
//
//import java.io.File;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.Base64;
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api")
//@CrossOrigin(origins = "*")
//public class ChatController {
//
//    @Autowired
//    private OllamaService ollamaService;
//
//    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> textChat(@RequestBody Map<String, Object> payload) {
//        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
//        return ollamaService.chatStream("qwen2.5:7b", messages);
//    }
//
//    @PostMapping(value = "/chat/media", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> mediaChat(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam("type") String type,
//            @RequestParam(value = "prompt", defaultValue = "请描述这个文件") String prompt) throws Exception {
//
//        if ("image".equals(type)) {
//            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
//            String visionResult = ollamaService.analyzeImage(base64, prompt);
//            return Flux.just(visionResult);
//        } else if ("audio".equals(type) || "video".equals(type)) {
//            return Flux.just("音视频处理逻辑已在后端框架中预留，请确保本地安装了FFmpeg和Whisper。");
//        }
//        return Flux.just("不支持的文件类型");
//    }
//}




package com.example.ai.controller;

import com.example.ai.service.OllamaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private OllamaService ollamaService;

    // 基础文本对话（支持流式）
//    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> textChat(@RequestBody Map<String, Object> payload) {
//        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
//        return ollamaService.chatStream("qwen2.5:7b", messages);
//    }
    // 基础文本对话（非流式）
    @PostMapping(value = "/chat")
    public String textChat(@RequestBody Map<String, Object> payload) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        // 收集流式响应为完整字符串
        return ollamaService.chatStream("qwen2.5:7b", messages).collectList().block().stream().collect(Collectors.joining());
    }


    // 多模态处理核心接口
    // 原先流式
//    @PostMapping(value = "/chat/media", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> mediaChat(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam("type") String type,
//            @RequestParam(value = "prompt", defaultValue = "请描述这个文件") String prompt) throws Exception {
//
//        // 创建临时目录存放处理文件
//        Path tempDir = Files.createTempDirectory("ai_media_");
//        File inputFile = new File(tempDir.toFile(), UUID.randomUUID() + "_" + file.getOriginalFilename());
//        file.transferTo(inputFile);
//
//        try {
//            if ("image".equals(type)) {
//                String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(inputFile.toPath()));
//                return Flux.just(ollamaService.analyzeImage(base64, prompt));
//
//            } else if ("audio".equals(type)) {
//                // 1. 调用 Whisper 转文字
//                String transcript = executeWhisper(inputFile, tempDir);
//                // 2. 将识别出的文字发给 Qwen2.5 进一步回答
//                String finalPrompt = "用户上传了语音，识别内容为：\"" + transcript + "\"。请根据内容回答问题：" + prompt;
//                return ollamaService.chatStream("qwen2.5:7b", List.of(Map.of("role", "user", "content", finalPrompt)));
//
//            } else if ("video".equals(type)) {
//                // 1. FFmpeg 抽帧
//                File frame = new File(tempDir.toFile(), "frame.jpg");
//                executeCommand("ffmpeg -y -i " + inputFile.getAbsolutePath() + " -ss 00:00:01 -vframes 1 " + frame.getAbsolutePath());
//
//                // 2. FFmpeg 提音
//                File audio = new File(tempDir.toFile(), "audio.wav");
//                executeCommand("ffmpeg -y -i " + inputFile.getAbsolutePath() + " -vn -acodec pcm_s16le -ar 16000 -ac 1 " + audio.getAbsolutePath());
//
//                // 3. Whisper 识别语音
//                String transcript = executeWhisper(audio, tempDir);
//
//                // 4. LLaVA 识别画面
//                String base64Frame = Base64.getEncoder().encodeToString(Files.readAllBytes(frame.toPath()));
//                String visionDesc = ollamaService.analyzeImage(base64Frame, "请描述视频画面内容");
//
//                // 5. 综合回答
//                String finalPrompt = String.format("视频分析报告：\n画面内容：%s\n语音内容：%s\n请结合以上信息回答用户问题：%s",
//                        visionDesc, transcript, prompt);
//                return ollamaService.chatStream("qwen2.5:7b", List.of(Map.of("role", "user", "content", finalPrompt)));
//            }
//        } finally {
//            // 以后可以异步清理临时文件，这里为了简单先不删，方便你调试看中间产物
//            System.out.println("处理完成，文件保存在: " + tempDir.toAbsolutePath());
//        }
//
//        return Flux.just("处理失败或不支持的类型");
//    }
    // 多模态处理核心接口（非流式）
    @PostMapping(value = "/chat/media")
    public String mediaChat(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @RequestParam(value = "prompt", defaultValue = "请描述这个文件") String prompt) throws Exception {

        // 创建临时目录存放处理文件
        Path tempDir = Files.createTempDirectory("ai_media_");
        File inputFile = new File(tempDir.toFile(), UUID.randomUUID() + "_" + file.getOriginalFilename());
        file.transferTo(inputFile);

        try {
            if ("image".equals(type)) {
                String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(inputFile.toPath()));
                return ollamaService.analyzeImage(base64, prompt);

            } else if ("audio".equals(type)) {
                // 1. 调用 Whisper 转文字
                String transcript = executeWhisper(inputFile, tempDir);
                // 2. 将识别出的文字发给 Qwen2.5 进一步回答
                String finalPrompt = "用户上传了语音，识别内容为：\"" + transcript + "\"。请根据内容回答问题：" + prompt;
                return ollamaService.chatStream("qwen2.5:7b", List.of(Map.of("role", "user", "content", finalPrompt)))
                        .collectList().block().stream().collect(Collectors.joining());

            } else if ("video".equals(type)) {
                // 1. FFmpeg 抽帧
                File frame = new File(tempDir.toFile(), "frame.jpg");
                executeCommand("ffmpeg -y -i " + inputFile.getAbsolutePath() + " -ss 00:00:01 -vframes 1 " + frame.getAbsolutePath());

                // 2. FFmpeg 提音
                File audio = new File(tempDir.toFile(), "audio.wav");
                executeCommand("ffmpeg -y -i " + inputFile.getAbsolutePath() + " -vn -acodec pcm_s16le -ar 16000 -ac 1 " + audio.getAbsolutePath());

                // 3. Whisper 识别语音
                String transcript = executeWhisper(audio, tempDir);

                // 4. LLaVA 识别画面
                String base64Frame = Base64.getEncoder().encodeToString(Files.readAllBytes(frame.toPath()));
                String visionDesc = ollamaService.analyzeImage(base64Frame, "请描述视频画面内容");

                // 5. 综合回答
                String finalPrompt = String.format("视频分析报告：\n画面内容：%s\n语音内容：%s\n请结合以上信息回答用户问题：%s",
                        visionDesc, transcript, prompt);
                return ollamaService.chatStream("qwen2.5:7b", List.of(Map.of("role", "user", "content", finalPrompt)))
                        .collectList().block().stream().collect(Collectors.joining());
            }
        } finally {
            // 以后可以异步清理临时文件，这里为了简单先不删，方便你调试看中间产物
            System.out.println("处理完成，文件保存在: " + tempDir.toAbsolutePath());
        }
        return "处理失败或不支持的类型";
    }


    // 封装 Whisper 调用逻辑
    private String executeWhisper(File audioFile, Path outputDir) throws Exception {
        // 命令：whisper <文件> --model small --language Chinese --output_format txt --output_dir <目录>
        String command = String.format("whisper %s --model small --language Chinese --output_format txt --output_dir %s",
                audioFile.getAbsolutePath(), outputDir.toAbsolutePath());
        executeCommand(command);

        // Whisper 会生成同名的 .txt 文件
        File resultTxt = new File(audioFile.getAbsolutePath().replaceAll("\\.[^.]+$", "") + ".txt");
        if (resultTxt.exists()) {
            return Files.readString(resultTxt.toPath());
        }
        return "[语音识别失败]";
    }

    // 核心：执行系统命令行工具
    private void executeCommand(String command) throws Exception {
        System.out.println("正在执行命令: " + command);
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 实时打印命令行输出到 IDEA 控制台，方便你观察进度
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("  [CLI LOG]: " + line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("命令执行失败，退出码: " + exitCode);
        }
    }
}