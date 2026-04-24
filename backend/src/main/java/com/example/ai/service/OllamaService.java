package com.example.ai.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OllamaService {

    private final WebClient webClient;

//    public OllamaService() {
//        this.webClient = WebClient.create("http://localhost:11434");
//    }
    public OllamaService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("http://localhost:11434")  // 确保这里指向正确的 Ollama 地址
            .build();
}

//    public Flux<String> chatStream(String model, List<Map<String, Object>> messages) {
//        Map<String, Object> request = new HashMap<>();
//        request.put("model", model);
//        request.put("messages", messages);
//        request.put("stream", true);
//
//        return webClient.post()
//                .uri("/api/chat")
//                .contentType(MediaType.APPLICATION_JSON)
//                .bodyValue(request)
//                .retrieve()
//                .bodyToFlux(Map.class)
//                .map(response -> {
//                    Map<String, Object> message = (Map<String, Object>) response.get("message");
//                    return message != null ? message.get("content").toString() : "";
//                });
//    }
public Flux<String> chatStream(String model, List<Map<String, Object>> messages) {
    Map<String, Object> request = new HashMap<>();
    request.put("model", model);
    request.put("messages", messages);
    request.put("stream", true);
    return webClient.post()
            .uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException("Ollama API 错误: " + body)))
            )
            .bodyToFlux(Map.class)
            .map(response -> {
                Map<String, Object> message = (Map<String, Object>) response.get("message");
                return message != null ? message.get("content").toString() : "";
            })
            .onErrorResume(e -> {
                System.err.println("聊天流处理错误: " + e.getMessage());
                return Flux.just("抱歉，处理您的请求时出现了错误: " + e.getMessage());
            });
}



    public String analyzeImage(String base64Image, String prompt) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", "llava:7b");
        request.put("prompt", prompt);
        request.put("images", List.of(base64Image));
        request.put("stream", false);

        Map response = webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        return response != null ? response.get("response").toString() : "解析失败";
    }
}
