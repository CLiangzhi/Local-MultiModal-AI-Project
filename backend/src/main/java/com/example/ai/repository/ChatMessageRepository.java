package com.example.ai.repository;

import com.example.ai.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    // 商业核心逻辑：根据 userId 查询该用户的历史聊天记录，按时间升序
    List<ChatMessageEntity> findByUserIdOrderByIdAsc(String userId);
}