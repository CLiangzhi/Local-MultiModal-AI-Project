package com.example.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "chat_history")
public class ChatMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    private String role; // "user", "assistant", "tool", "agent"

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "parent_message_id")
    private Long parentMessageId;

    @Column(name = "tool_calls", columnDefinition = "MEDIUMTEXT")
    private String toolCalls; // JSON: [{"name":"read_file","args":{...},"result":"..."}]

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
