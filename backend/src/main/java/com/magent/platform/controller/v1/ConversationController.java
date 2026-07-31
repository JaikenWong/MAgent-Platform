package com.magent.platform.controller.v1;

import com.magent.platform.common.R;
import com.magent.platform.dto.PageQuery;
import com.magent.platform.dto.PageResult;
import com.magent.platform.entity.Conversation;
import com.magent.platform.mapper.ConversationMapper;
import com.magent.platform.mapper.MessageMapper;
import com.magent.platform.mapper.TaskMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final TaskMapper taskMapper;

    @GetMapping
    public R<PageResult<Conversation>> list(PageQuery q) {
        Page<Conversation> page = new Page<>(q.safePage(), q.safeSize());
        conversationMapper.selectPage(page, null);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/{id}")
    public R<Conversation> get(@PathVariable String id) {
        return R.ok(conversationMapper.selectById(id));
    }

    @GetMapping("/{id}/messages")
    public R<?> messages(@PathVariable String id) {
        return R.ok(messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.magent.platform.entity.Message>()
                        .eq("conversation_id", id).orderByAsc("created_at")));
    }

    @GetMapping("/{id}/tasks")
    public R<?> tasks(@PathVariable String id) {
        return R.ok(taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.magent.platform.entity.Task>()
                        .eq("conversation_id", id).orderByAsc("created_at")));
    }
}