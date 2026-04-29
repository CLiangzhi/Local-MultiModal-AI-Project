package com.example.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data // 自动生成 getter/setter (依赖 Lombok)
@Entity
@Table(name = "chat_history")
public class ChatMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    private String role; // "user" 或 "assistant"

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}