package com.magent.platform.controller.v1;

import com.magent.platform.common.CryptoUtil;
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
        encryptSensitiveFields(entity);
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
        // 空字段保留 DB 原值 (前端编辑时清空敏感字段)
        FeishuBot db = mapper.selectById(id);
        if (db != null) {
            if (isBlank(entity.getAppSecret())) entity.setAppSecret(db.getAppSecret());
            if (isBlank(entity.getVerificationToken())) entity.setVerificationToken(db.getVerificationToken());
            if (isBlank(entity.getEncryptKey())) entity.setEncryptKey(db.getEncryptKey());
        }
        encryptSensitiveFields(entity);
        mapper.updateById(entity);
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

    // ───── helpers: 敏感字段 AES 加密 ─────

    private void encryptSensitiveFields(FeishuBot bot) {
        bot.setAppSecret(encryptIfPlain(bot.getAppSecret()));
        bot.setVerificationToken(encryptIfPlain(bot.getVerificationToken()));
        bot.setEncryptKey(encryptIfPlain(bot.getEncryptKey()));
    }

    /** 已是密文则原样返回, 明文则加密. */
    private String encryptIfPlain(String s) {
        if (isBlank(s)) return s;
        try {
            CryptoUtil.decrypt(s);
            return s; // 已密文
        } catch (Exception e) {
            return CryptoUtil.encrypt(s); // 明文 -> 加密
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
