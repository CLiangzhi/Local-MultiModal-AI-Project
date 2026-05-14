package com.example.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentService {

    @Autowired
    private RagService ragService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("<tool_call>\\s*([\\s\\S]*?)\\s*</tool_call>");

    private static final int MAX_AGENT_ITERATIONS = 5;

    public String buildAgentSystemPrompt() {
        return """
                You are an AI Agent with the ability to use tools. You can call tools to help answer the user's question.

                ## Available Tools

                1. **read_file(path)** - Read the content of a file on the local filesystem.
                2. **write_file(path, content)** - Write content to a file. Creates the file if it doesn't exist.
                3. **execute_command(command)** - Execute a shell command and get the output. Use with caution.
                4. **search_knowledge(query)** - Search the user's private knowledge base for relevant documents.
                5. **list_knowledge_files()** - List all files in the user's knowledge base.

                ## How to Use Tools

                When you need to use a tool, respond with EXACTLY this format:

                <tool_call>
                {"name": "tool_name", "arguments": {"arg1": "value1", "arg2": "value2"}}
                </tool_call>

                After the tool result is provided, you can either:
                - Use another tool if needed
                - Provide your final answer to the user

                ## Important Rules

                - Only use tools when necessary. For simple conversations, just respond directly.
                - Use **execute_command** carefully. Never execute destructive commands (rm -rf, format, etc.).
                - When reading/writing files, use absolute paths.
                - Be concise and helpful. Explain what you're doing when using tools.
                - You may use at most %d tool calls per response.
                """.formatted(MAX_AGENT_ITERATIONS);
    }

    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "read_file",
                        "description", "Read the content of a file from the local filesystem",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "path", Map.of("type", "string", "description", "Absolute path to the file")
                                ),
                                "required", List.of("path")
                        )
                )
        ));

        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "write_file",
                        "description", "Write content to a file on the local filesystem. Creates the file if it doesn't exist.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "path", Map.of("type", "string", "description", "Absolute path to the file"),
                                        "content", Map.of("type", "string", "description", "Content to write to the file")
                                ),
                                "required", List.of("path", "content")
                        )
                )
        ));

        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "execute_command",
                        "description", "Execute a shell command and return its output. Use with caution. Avoid destructive commands.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "command", Map.of("type", "string", "description", "The shell command to execute")
                                ),
                                "required", List.of("command")
                        )
                )
        ));

        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "search_knowledge",
                        "description", "Search the user's private knowledge base for relevant document chunks",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string", "description", "The search query to find relevant documents")
                                ),
                                "required", List.of("query")
                        )
                )
        ));

        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "list_knowledge_files",
                        "description", "List all files currently in the user's knowledge base",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                )
        ));

        return tools;
    }

    public String executeTool(String toolName, Map<String, Object> args, String userId) {
        try {
            return switch (toolName) {
                case "read_file" -> executeReadFile((String) args.get("path"));
                case "write_file" -> executeWriteFile((String) args.get("path"), (String) args.get("content"));
                case "execute_command" -> executeShellCommand((String) args.get("command"));
                case "search_knowledge" -> executeSearchKnowledge((String) args.get("query"), userId);
                case "list_knowledge_files" -> executeListKnowledgeFiles(userId);
                default -> "Unknown tool: " + toolName;
            };
        } catch (Exception e) {
            return "Tool execution error: " + e.getMessage();
        }
    }

    public Map<String, Object> tryParseToolCall(String response) {
        Matcher matcher = TOOL_CALL_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                String json = matcher.group(1).trim();
                return objectMapper.readValue(json, Map.class);
            } catch (JsonProcessingException e) {
                return null;
            }
        }
        return null;
    }

    public String formatToolResult(String toolName, String result) {
        return String.format("\n\n<tool_result name=\"%s\">\n%s\n</tool_result>\n\n", toolName, result);
    }

    // --- Tool implementations ---

    private String executeReadFile(String path) {
        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return "Error: File not found: " + path;
            }
            if (!Files.isReadable(filePath)) {
                return "Error: File is not readable: " + path;
            }
            long size = Files.size(filePath);
            if (size > 1024 * 1024) { // 1MB limit
                return "Error: File is too large (" + (size / 1024) + " KB). Maximum is 1MB.";
            }
            return Files.readString(filePath);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    private String executeWriteFile(String path, String content) {
        try {
            Path filePath = Paths.get(path);
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, content);
            return "Successfully wrote " + content.length() + " characters to " + path;
        } catch (Exception e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    private String executeShellCommand(String command) {
        try {
            // Safety: block obviously destructive commands
            String lowerCmd = command.toLowerCase().trim();
            if (lowerCmd.startsWith("rm -rf") || lowerCmd.startsWith("rm -r") ||
                lowerCmd.contains("> /dev/") || lowerCmd.startsWith("format") ||
                lowerCmd.startsWith("mkfs") || lowerCmd.startsWith("dd if=") ||
                lowerCmd.contains("del /f") || lowerCmd.startsWith("deltree")) {
                return "Error: Destructive command blocked for safety.";
            }

            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), isWindows ? "GBK" : "UTF-8"))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 500) {
                    output.append(line).append("\n");
                    lineCount++;
                }
                if (lineCount >= 500) {
                    output.append("... (output truncated at 500 lines)");
                }
            }

            int exitCode = process.waitFor();
            if (output.isEmpty()) {
                return "Command executed with exit code " + exitCode + " (no output)";
            }
            return "[Exit code: " + exitCode + "]\n" + output.toString().trim();
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    private String executeSearchKnowledge(String query, String userId) {
        try {
            String context = ragService.retrieveContext(userId, query);
            if (context.isEmpty()) {
                return "No relevant documents found in your knowledge base for: " + query;
            }
            return "Found relevant content from knowledge base:\n\n" + context;
        } catch (Exception e) {
            return "Error searching knowledge base: " + e.getMessage();
        }
    }

    private String executeListKnowledgeFiles(String userId) {
        try {
            List<String> files = knowledgeBaseService.listUserFiles(userId);
            if (files.isEmpty()) {
                return "Your knowledge base is empty. No files uploaded yet.";
            }
            return "Files in your knowledge base:\n- " + String.join("\n- ", files);
        } catch (Exception e) {
            return "Error listing knowledge base files: " + e.getMessage();
        }
    }
}
