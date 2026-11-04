package com.zimo.regtech.ai.controller;

import com.zimo.regtech.ai.entity.ChatMessage;
import com.zimo.regtech.ai.entity.Conversation;
import com.zimo.regtech.ai.mapper.AiMapper;
import com.zimo.regtech.ai.service.ChatService;
import com.zimo.regtech.common.context.UserContext;
import com.zimo.regtech.common.exception.BizException;
import com.zimo.regtech.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final AiMapper aiMapper;
    private final ChatService chatService;

    @GetMapping
    public R<List<Conversation>> list() {
        return R.ok(aiMapper.selectConversations(UserContext.getUserId(), 60));
    }

    @PostMapping
    public R<Conversation> create(@RequestBody(required = false) Map<String, String> body) {
        Conversation conversation = new Conversation();
        conversation.setUserId(UserContext.getUserId());
        conversation.setTitle(body == null || body.get("title") == null ? "新会话" : body.get("title"));
        conversation.setModel(chatService.resolveModel(body == null ? null : body.get("model")));
        conversation.setScene(body == null || body.get("scene") == null ? "chat" : body.get("scene"));
        aiMapper.insertConversation(conversation);
        return R.ok(conversation);
    }

    @GetMapping("/{id}/messages")
    public R<List<ChatMessage>> messages(@PathVariable Long id) {
        ensureOwner(id);
        return R.ok(aiMapper.selectMessages(id));
    }

    @PutMapping("/{id}")
    public R<Void> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ensureOwner(id);
        String title = body.getOrDefault("title", "新会话");
        aiMapper.updateConversationTitle(id, title);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ensureOwner(id);
        aiMapper.deleteConversation(id);
        return R.ok();
    }

    private void ensureOwner(Long id) {
        Conversation conversation = aiMapper.selectConversation(id);
        if (conversation == null) {
            throw new BizException("会话不存在");
        }
        Long userId = UserContext.getUserId();
        if (conversation.getUserId() != null && !Objects.equals(conversation.getUserId(), userId)) {
            throw new BizException("无权访问该会话");
        }
    }
}
