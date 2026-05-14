package com.example.ai.controller;

import com.example.ai.entity.ChatMessageEntity;
import com.example.ai.entity.ConversationEntity;
import com.example.ai.repository.ChatMessageRepository;
import com.example.ai.repository.ConversationRepository;
import com.example.ai.service.AgentService;
import com.example.ai.service.KnowledgeBaseService;
import com.example.ai.service.OllamaService;
import com.example.ai.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private OllamaService ollamaService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private RagService ragService;

    @Autowired
    private AgentService agentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String getCurrentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // ================== Conversation Management ==================

    @GetMapping("/conversations")
    public List<Map<String, Object>> getConversations() {
        String userId = getCurrentUserId();
        List<ConversationEntity> convs = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        // Auto-migrate: if there are messages without conversation_id, create a default conversation
        if (convs.isEmpty()) {
            List<ChatMessageEntity> orphanMessages = chatMessageRepository.findByUserIdOrderByIdAsc(userId);
            if (!orphanMessages.isEmpty()) {
                ConversationEntity defaultConv = new ConversationEntity();
                defaultConv.setUserId(userId);
                defaultConv.setTitle("默认会话");
                defaultConv = conversationRepository.save(defaultConv);

                for (ChatMessageEntity msg : orphanMessages) {
                    msg.setConversationId(defaultConv.getId());
                    if (msg.getParentMessageId() == null) {
                        msg.setParentMessageId(msg.getId() > 1 ? msg.getId() - 1 : null);
                    }
                }
                chatMessageRepository.saveAll(orphanMessages);

                // Set active leaf
                if (!orphanMessages.isEmpty()) {
                    defaultConv.setActiveLeafMessageId(orphanMessages.get(orphanMessages.size() - 1).getId());
                    conversationRepository.save(defaultConv);
                }
                convs = List.of(defaultConv);
            }
        }

        return convs.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("title", c.getTitle());
            m.put("createdAt", c.getCreatedAt());
            m.put("updatedAt", c.getUpdatedAt());
            return m;
        }).collect(Collectors.toList());
    }

    @PostMapping("/conversations")
    public Map<String, Object> createConversation(@RequestBody Map<String, String> payload) {
        String userId = getCurrentUserId();
        ConversationEntity conv = new ConversationEntity();
        conv.setUserId(userId);
        conv.setTitle(payload.getOrDefault("title", "新会话"));
        conv = conversationRepository.save(conv);
        return Map.of("id", conv.getId(), "title", conv.getTitle());
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, Object> deleteConversation(@PathVariable Long id) {
        List<ChatMessageEntity> msgs = chatMessageRepository.findByConversationIdOrderByIdAsc(id);
        chatMessageRepository.deleteAll(msgs);
        conversationRepository.deleteById(id);
        return Map.of("success", true);
    }

    @PutMapping("/conversations/{id}")
    public Map<String, Object> renameConversation(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        ConversationEntity conv = conversationRepository.findById(id).orElse(null);
        if (conv != null && payload.containsKey("title")) {
            conv.setTitle(payload.get("title"));
            conversationRepository.save(conv);
        }
        return Map.of("success", true);
    }

    // ================== Conversation Messages ==================

    @GetMapping("/conversations/{id}/messages")
    public List<Map<String, Object>> getConversationMessages(@PathVariable Long id) {
        List<ChatMessageEntity> msgs = chatMessageRepository.findByConversationIdOrderByIdAsc(id);
        List<Map<String, Object>> result = new ArrayList<>();

        // Build children map for branch detection
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (ChatMessageEntity msg : msgs) {
            if (msg.getParentMessageId() != null) {
                childrenMap.computeIfAbsent(msg.getParentMessageId(), k -> new ArrayList<>()).add(msg.getId());
            }
        }

        // Walk the tree starting from root, following first child at each fork (active branch)
        ConversationEntity conv = conversationRepository.findById(id).orElse(null);
        Long activeLeaf = conv != null ? conv.getActiveLeafMessageId() : null;

        // Build the active path: walk backwards from active leaf to root
        Map<Long, ChatMessageEntity> msgMap = new HashMap<>();
        for (ChatMessageEntity msg : msgs) {
            msgMap.put(msg.getId(), msg);
        }

        Set<Long> activePath = new HashSet<>();
        Long current = activeLeaf;
        while (current != null) {
            activePath.add(current);
            ChatMessageEntity msg = msgMap.get(current);
            if (msg != null) {
                current = msg.getParentMessageId();
            } else {
                break;
            }
        }

        // Return messages in order, marking branch points
        for (ChatMessageEntity msg : msgs) {
            Map<String, Object> m = messageToMap(msg);
            List<Long> children = childrenMap.get(msg.getId());
            boolean hasBranches = children != null && children.size() > 1;
            m.put("hasBranches", hasBranches);
            m.put("isOnActivePath", activePath.contains(msg.getId()));

            if (hasBranches) {
                // Find sibling branch messages (children not on active path)
                List<Map<String, Object>> branchOptions = new ArrayList<>();
                for (Long childId : children) {
                    if (!activePath.contains(childId)) {
                        ChatMessageEntity childMsg = msgMap.get(childId);
                        if (childMsg != null && "user".equals(childMsg.getRole())) {
                            Map<String, Object> branch = new HashMap<>();
                            branch.put("messageId", childId);
                            branch.put("preview", childMsg.getContent().substring(0, Math.min(50, childMsg.getContent().length())));
                            branchOptions.add(branch);
                        }
                    }
                }
                if (!branchOptions.isEmpty()) {
                    m.put("branchOptions", branchOptions);
                }
            }

            result.add(m);
        }

        return result;
    }

    @GetMapping("/conversations/{id}/branch-preview/{messageId}")
    public List<Map<String, Object>> getBranchMessages(@PathVariable Long id, @PathVariable Long messageId) {
        // Walk from the given messageId to the leaf of its branch
        Map<Long, ChatMessageEntity> msgMap = new HashMap<>();
        List<ChatMessageEntity> msgs = chatMessageRepository.findByConversationIdOrderByIdAsc(id);
        for (ChatMessageEntity msg : msgs) msgMap.put(msg.getId(), msg);

        // Build children map
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (ChatMessageEntity msg : msgs) {
            if (msg.getParentMessageId() != null) {
                childrenMap.computeIfAbsent(msg.getParentMessageId(), k -> new ArrayList<>()).add(msg.getId());
            }
        }

        // Walk forward from the branch point following first child
        List<Map<String, Object>> result = new ArrayList<>();
        Long current = messageId;
        while (current != null) {
            ChatMessageEntity msg = msgMap.get(current);
            if (msg == null) break;
            result.add(messageToMap(msg));
            List<Long> children = childrenMap.get(current);
            current = (children != null && !children.isEmpty()) ? children.get(0) : null;
        }
        return result;
    }

    @PostMapping("/conversations/{id}/switch-branch/{messageId}")
    public Map<String, Object> switchBranch(@PathVariable Long id, @PathVariable Long messageId) {
        // Walk forward from the given messageId to find the new leaf
        Map<Long, ChatMessageEntity> msgMap = new HashMap<>();
        List<ChatMessageEntity> msgs = chatMessageRepository.findByConversationIdOrderByIdAsc(id);
        for (ChatMessageEntity msg : msgs) msgMap.put(msg.getId(), msg);

        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (ChatMessageEntity msg : msgs) {
            if (msg.getParentMessageId() != null) {
                childrenMap.computeIfAbsent(msg.getParentMessageId(), k -> new ArrayList<>()).add(msg.getId());
            }
        }

        // Walk forward to find the new leaf
        Long current = messageId;
        Long leaf = messageId;
        while (current != null) {
            ChatMessageEntity msg = msgMap.get(current);
            if (msg == null) break;
            leaf = current;
            List<Long> children = childrenMap.get(current);
            current = (children != null && !children.isEmpty()) ? children.get(0) : null;
        }

        ConversationEntity conv = conversationRepository.findById(id).orElse(null);
        if (conv != null) {
            conv.setActiveLeafMessageId(leaf);
            conversationRepository.save(conv);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("activeLeafMessageId", leaf);
        return result;
    }

    // ================== Branch Creation ==================

    @PostMapping("/conversations/{id}/branch")
    public Map<String, Object> createBranch(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Long parentMessageId = payload.get("parentMessageId") != null
                ? Long.valueOf(payload.get("parentMessageId").toString()) : null;
        String content = (String) payload.get("content");

        String userId = getCurrentUserId();
        String branchPreview = content != null ? content.substring(0, Math.min(50, content.length())) : "";

        return Map.of("success", true, "branchPreview", branchPreview,
                "parentMessageId", parentMessageId);
    }

    // ================== Chat History (legacy) ==================

    @GetMapping("/history")
    public List<ChatMessageEntity> getHistory() {
        // Return all messages (will be filtered by conversation on frontend)
        return chatMessageRepository.findByUserIdOrderByIdAsc(getCurrentUserId());
    }

    // ================== Knowledge Base ==================

    @GetMapping("/knowledge/files")
    public List<String> getKnowledgeFiles() {
        return knowledgeBaseService.listUserFiles(getCurrentUserId());
    }

    @PostMapping("/knowledge/upload")
    public Map<String, Object> uploadKnowledgeFile(@RequestParam("file") MultipartFile file) {
        try {
            final String currentUserId = getCurrentUserId();
            knowledgeBaseService.saveUserFile(currentUserId, file);
            Path savedFilePath = Path.of("local_data/knowledge_base", currentUserId, file.getOriginalFilename());
            new Thread(() -> ragService.ingestDocumentForUser(currentUserId, savedFilePath)).start();
            return Map.of("success", true, "message", "文件上传成功，正在背景构建专属知识索引...");
        } catch (Exception e) {
            return Map.of("success", false, "message", "文件存储失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/knowledge/files/{filename:.+}")
    public Map<String, Object> deleteKnowledgeFile(@PathVariable String filename) {
        String userId = getCurrentUserId();
        boolean deleted = knowledgeBaseService.deleteUserFile(userId, filename);
        if (deleted) {
            new Thread(() -> ragService.deleteFileVectors(userId, filename)).start();
            return Map.of("success", true, "message", "文件已删除，向量库已更新");
        }
        return Map.of("success", false, "message", "文件不存在或删除失败");
    }

    @PostMapping("/knowledge/rebuild")
    public Map<String, Object> rebuildKnowledgeBase() {
        String userId = getCurrentUserId();
        new Thread(() -> ragService.rebuildStore(userId)).start();
        return Map.of("success", true, "message", "正在后台重建知识库索引...");
    }

    // ================== Helper ==================

    private Map<String, Object> messageToMap(ChatMessageEntity msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", msg.getId());
        m.put("role", msg.getRole());
        m.put("content", msg.getContent());
        m.put("conversationId", msg.getConversationId());
        m.put("parentMessageId", msg.getParentMessageId());
        m.put("createdAt", msg.getCreatedAt());

        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            try {
                m.put("toolCalls", objectMapper.readValue(msg.getToolCalls(), List.class));
            } catch (Exception e) {
                m.put("toolCalls", List.of());
            }
        }
        return m;
    }

    private ChatMessageEntity saveMessage(String userId, String role, String content,
                                           Long conversationId, Long parentMessageId, String toolCalls) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setUserId(userId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setConversationId(conversationId);
        entity.setParentMessageId(parentMessageId);
        entity.setToolCalls(toolCalls);
        return chatMessageRepository.save(entity);
    }

    private ChatMessageEntity saveMessage(String userId, String role, String content,
                                           Long conversationId, Long parentMessageId) {
        return saveMessage(userId, role, content, conversationId, parentMessageId, null);
    }

    // ================== Text Chat (streaming + branching support) ==================

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> textChat(@RequestBody Map<String, Object> payload) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        Map<String, Object> lastMsg = messages.get(messages.size() - 1);
        String userQuery = lastMsg.get("content").toString();

        final String currentUserId = getCurrentUserId();

        // Get or create conversation
        Long conversationId = payload.get("conversationId") != null
                ? Long.valueOf(payload.get("conversationId").toString()) : null;
        Long parentMessageId = payload.get("parentMessageId") != null
                ? Long.valueOf(payload.get("parentMessageId").toString()) : null;

        if (conversationId == null) {
            // Create default conversation
            ConversationEntity conv = new ConversationEntity();
            conv.setUserId(currentUserId);
            conv.setTitle(userQuery.length() > 30 ? userQuery.substring(0, 30) + "..." : userQuery);
            conv = conversationRepository.save(conv);
            conversationId = conv.getId();
        }

        // Save user message
        ChatMessageEntity userMsg = saveMessage(currentUserId, "user", userQuery, conversationId, parentMessageId);

        // RAG enhancement
        String retrievedContext = ragService.retrieveContext(currentUserId, userQuery);
        if (!retrievedContext.isEmpty()) {
            System.out.println("[RAG] 命中本地知识库，已注入上下文");
            String enhancedPrompt = String.format(
                    "以下是知识库中与问题相关的文档内容（按原文顺序排列，来源可能有多个文档）：\n\n%s\n\n【用户问题】：%s\n\n请根据以上资料回答。如果资料中有相关信息，请完整引用。如果资料中没有提及，请明确说'当前知识库中未找到相关信息'，禁止编造或猜测。",
                    retrievedContext, userQuery
            );
            lastMsg.put("content", enhancedPrompt);
        }

        final Long finalConversationId = conversationId;
        final Long userMessageId = userMsg.getId();
        StringBuilder fullResponse = new StringBuilder();

        return ollamaService.chatStream("qwen2.5:7b", messages)
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    ChatMessageEntity assistantMsg = saveMessage(currentUserId, "assistant",
                            fullResponse.toString(), finalConversationId, userMessageId);
                    // Update conversation's active leaf
                    ConversationEntity conv = conversationRepository.findById(finalConversationId).orElse(null);
                    if (conv != null) {
                        conv.setActiveLeafMessageId(assistantMsg.getId());
                        conversationRepository.save(conv);
                    }
                });
    }

    // ================== AI Agent Chat (streaming tool calling) ==================

    @PostMapping(value = "/chat/agent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agentChat(@RequestBody Map<String, Object> payload) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        Map<String, Object> lastMsg = messages.get(messages.size() - 1);
        String userQuery = lastMsg.get("content").toString();

        final String currentUserId = getCurrentUserId();

        Long conversationId = payload.get("conversationId") != null
                ? Long.valueOf(payload.get("conversationId").toString()) : null;
        Long parentMessageId = payload.get("parentMessageId") != null
                ? Long.valueOf(payload.get("parentMessageId").toString()) : null;

        if (conversationId == null) {
            ConversationEntity conv = new ConversationEntity();
            conv.setUserId(currentUserId);
            conv.setTitle(userQuery.length() > 30 ? userQuery.substring(0, 30) + "..." : userQuery);
            conv = conversationRepository.save(conv);
            conversationId = conv.getId();
        }

        final Long finalConversationId = conversationId;
        ChatMessageEntity userMsg = saveMessage(currentUserId, "user", userQuery, finalConversationId, parentMessageId);
        final Long userMessageId = userMsg.getId();

        // RAG enhancement
        String retrievedContext = ragService.retrieveContext(currentUserId, userQuery);
        if (!retrievedContext.isEmpty()) {
            String enhancedPrompt = String.format(
                    "以下是知识库中与问题相关的文档内容（按原文顺序排列，来源可能有多个文档）：\n\n%s\n\n【用户问题】：%s\n\n请根据以上资料回答。如果资料中有相关信息，请完整引用。如果资料中没有提及，请明确说'当前知识库中未找到相关信息'，禁止编造或猜测。",
                    retrievedContext, userQuery
            );
            lastMsg.put("content", enhancedPrompt);
        }

        // Build agent conversation with system prompt
        List<Map<String, Object>> agentMessages = new ArrayList<>();
        agentMessages.add(Map.of("role", "system", "content", agentService.buildAgentSystemPrompt()));
        agentMessages.addAll(messages);

        List<Map<String, Object>> toolCallLog = new ArrayList<>();
        List<Map<String, Object>> currentMessages = new ArrayList<>(agentMessages);

        return Flux.<String>create(sink -> {
            agentLoop(sink, currentMessages, toolCallLog, currentUserId, finalConversationId, userMessageId, 0);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void agentLoop(FluxSink<String> sink,
                           List<Map<String, Object>> currentMessages,
                           List<Map<String, Object>> toolCallLog,
                           String currentUserId, Long conversationId, Long userMessageId,
                           int iteration) {
        if (iteration >= 5) {
            sink.next("\n[Agent已达到最大调用次数]\n");
            finishAgent(sink, toolCallLog, currentUserId, conversationId, userMessageId);
            return;
        }

        final int iter = iteration + 1;
        sink.next("[AGENT_THINKING] 正在思考... (第" + iter + "轮)\n");

        // Stream LLM response in real-time, accumulate for tool call detection
        StringBuilder fullResponse = new StringBuilder();
        ollamaService.chatStream("qwen2.5:7b", currentMessages)
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(chunk -> {
                    fullResponse.append(chunk);
                    sink.next(chunk); // real-time streaming to frontend
                })
                .doOnComplete(() -> {
                    String response = fullResponse.toString();
                    Map<String, Object> toolCall = agentService.tryParseToolCall(response);

                    if (toolCall != null) {
                        String toolName = (String) toolCall.get("name");
                        Map<String, Object> toolArgs = (Map<String, Object>) toolCall.get("arguments");
                        if (toolArgs == null) toolArgs = Map.of();

                        // Send tool call event
                        try {
                            Map<String, Object> tcInfo = new HashMap<>();
                            tcInfo.put("name", toolName);
                            tcInfo.put("arguments", toolArgs);
                            sink.next("\n[TOOL_CALL] " + objectMapper.writeValueAsString(tcInfo) + "\n");

                            // Execute tool
                            String toolResult = agentService.executeTool(toolName, toolArgs, currentUserId);

                            // Send tool result
                            Map<String, Object> trInfo = new HashMap<>();
                            trInfo.put("name", toolName);
                            trInfo.put("result", toolResult);
                            sink.next("[TOOL_RESULT] " + objectMapper.writeValueAsString(trInfo) + "\n");

                            // Log
                            Map<String, Object> logEntry = new HashMap<>();
                            logEntry.put("name", toolName);
                            logEntry.put("arguments", toolArgs);
                            logEntry.put("result", toolResult);
                            toolCallLog.add(logEntry);

                            // Add to conversation context
                            currentMessages.add(Map.of("role", "assistant", "content", response));
                            currentMessages.add(Map.of("role", "user", "content",
                                    agentService.formatToolResult(toolName, toolResult)));

                            // Continue loop
                            agentLoop(sink, currentMessages, toolCallLog,
                                    currentUserId, conversationId, userMessageId, iter);
                        } catch (Exception e) {
                            sink.next("[AGENT_ERROR] " + e.getMessage() + "\n");
                            finishAgent(sink, toolCallLog, currentUserId, conversationId, userMessageId);
                        }
                    } else {
                        finishAgent(sink, toolCallLog, currentUserId, conversationId, userMessageId);
                    }
                })
                .doOnError(e -> {
                    sink.next("\n[AGENT_ERROR] Agent流异常: " + e.getMessage() + "\n");
                    finishAgent(sink, toolCallLog, currentUserId, conversationId, userMessageId);
                })
                .subscribe();
    }

    private void finishAgent(FluxSink<String> sink,
                             List<Map<String, Object>> toolCallLog,
                             String currentUserId, Long conversationId, Long userMessageId) {
        // Save assistant message with tool call log
        StringBuilder finalContent = new StringBuilder("[Agent模式对话]\n");
        for (Map<String, Object> log : toolCallLog) {
            try {
                finalContent.append("\n🔧 调用工具: ").append(log.get("name"))
                        .append("\n参数: ").append(objectMapper.writeValueAsString(log.get("arguments")))
                        .append("\n结果: ").append(log.get("result")).append("\n");
            } catch (Exception e) {}
        }

        String tcLogJson = null;
        try {
            tcLogJson = objectMapper.writeValueAsString(toolCallLog);
        } catch (Exception e) {}

        ChatMessageEntity assistantMsg = saveMessage(currentUserId, "assistant",
                finalContent.toString(), conversationId, userMessageId, tcLogJson);

        ConversationEntity conv = conversationRepository.findById(conversationId).orElse(null);
        if (conv != null) {
            conv.setActiveLeafMessageId(assistantMsg.getId());
            conversationRepository.save(conv);
        }

        sink.complete();
    }

    // ================== Media Chat ==================

    @PostMapping(value = "/chat/media", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> mediaChat(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @RequestParam(value = "prompt", defaultValue = "请描述这个文件") String prompt,
            @RequestParam(value = "conversationId", required = false) Long conversationId,
            @RequestParam(value = "parentMessageId", required = false) Long parentMessageId) throws Exception {

        byte[] fileBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();

        final String currentUserId = getCurrentUserId();

        // Get or create conversation
        final Long finalConversationId;
        if (conversationId == null) {
            ConversationEntity conv = new ConversationEntity();
            conv.setUserId(currentUserId);
            conv.setTitle("媒体: " + originalFilename);
            conv = conversationRepository.save(conv);
            finalConversationId = conv.getId();
        } else {
            finalConversationId = conversationId;
        }

        ChatMessageEntity userMsg = saveMessage(currentUserId, "user",
                "[上传了媒体文件] " + prompt, finalConversationId, parentMessageId);
        final Long userMessageId = userMsg.getId();

        return Flux.<String>create(sink -> {
            Path tempDir = null;
            StringBuilder fullResponse = new StringBuilder();

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
                // Save assistant message
                ChatMessageEntity assistantMsg = saveMessage(currentUserId, "assistant",
                        fullResponse.toString(), finalConversationId, userMessageId);
                ConversationEntity conv = conversationRepository.findById(finalConversationId).orElse(null);
                if (conv != null) {
                    conv.setActiveLeafMessageId(assistantMsg.getId());
                    conversationRepository.save(conv);
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ================== Command-line Tools ==================

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
            while ((line = reader.readLine()) != null) { }
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
