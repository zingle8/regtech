package com.zimo.regtech.ai.mapper;

import com.zimo.regtech.ai.entity.ChatMessage;
import com.zimo.regtech.ai.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiMapper {

    int insertConversation(Conversation conversation);

    int updateConversationTitle(@Param("id") Long id, @Param("title") String title);

    int touchConversation(@Param("id") Long id, @Param("count") int count, @Param("model") String model);

    int deleteConversation(@Param("id") Long id);

    Conversation selectConversation(@Param("id") Long id);

    List<Conversation> selectConversations(@Param("userId") Long userId, @Param("limit") int limit);

    int insertMessage(ChatMessage message);

    List<ChatMessage> selectMessages(@Param("conversationId") Long conversationId);

    long countConversations();

    long countMessages();

    long countTodayMessages();

    List<Map<String, Object>> countByModel();
}
