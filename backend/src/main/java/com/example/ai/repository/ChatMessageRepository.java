package com.example.ai.repository;

import com.example.ai.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByUserIdOrderByIdAsc(String userId);

    List<ChatMessageEntity> findByConversationIdOrderByIdAsc(Long conversationId);

    @Query("SELECT m FROM ChatMessageEntity m WHERE m.conversationId = :convId AND " +
           "(m.parentMessageId = :parentId OR (m.parentMessageId IS NULL AND :parentId IS NULL))")
    List<ChatMessageEntity> findByConversationIdAndParentMessageId(
            @Param("convId") Long conversationId, @Param("parentId") Long parentMessageId);

    @Query("SELECT COUNT(m) > 0 FROM ChatMessageEntity m WHERE m.parentMessageId = :messageId")
    boolean existsByParentMessageId(@Param("messageId") Long messageId);
}
