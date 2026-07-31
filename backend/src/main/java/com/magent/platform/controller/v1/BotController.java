package com.magent.platform.controller.v1;

import com.magent.platform.common.R;
import com.magent.platform.entity.FeishuBot;
import com.magent.platform.mapper.FeishuBotMapper;
import com.magent.platform.service.feishu.FeishuLongConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bots")
@RequiredArgsConstructor
public class BotController extends CrudController<FeishuBot, FeishuBotMapper> {

    private final FeishuLongConnectionService longConnectionService;

    @Override
    protected Class<FeishuBot> entityClass() { return FeishuBot.class; }

    @Override
    @PostMapping
    public R<FeishuBot> create(@Valid @RequestBody FeishuBot entity) {
        mapper.insert(entity);
        if (Boolean.TRUE.equals(entity.getLongConnectionEnabled())) {
            longConnectionService.enable(entity.getId());
        }
        return R.ok(entity);
    }

    @Override
    @PutMapping("/{id}")
    public R<FeishuBot> update(@PathVariable String id, @RequestBody FeishuBot entity) {
        entity.setId(id);
        mapper.updateById(entity);
        // longConnectionEnabled 显式传入时同步长连接状态
        if (entity.getLongConnectionEnabled() != null) {
            if (entity.getLongConnectionEnabled()) {
                longConnectionService.enable(id);
            } else {
                longConnectionService.disable(id);
            }
        }
        return R.ok(entity);
    }

    @PostMapping("/{id}/long-connection/enable")
    public R<Void> enableLongConnection(@PathVariable String id) {
        FeishuBot update = new FeishuBot();
        update.setId(id);
        update.setLongConnectionEnabled(true);
        mapper.updateById(update);
        longConnectionService.enable(id);
        return R.ok();
    }

    @PostMapping("/{id}/long-connection/disable")
    public R<Void> disableLongConnection(@PathVariable String id) {
        FeishuBot update = new FeishuBot();
        update.setId(id);
        update.setLongConnectionEnabled(false);
        mapper.updateById(update);
        longConnectionService.disable(id);
        return R.ok();
    }
}
