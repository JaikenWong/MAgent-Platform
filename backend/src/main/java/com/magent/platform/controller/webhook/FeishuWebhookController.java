package com.magent.platform.controller.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.R;
import com.magent.platform.service.feishu.FeishuGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 飞书 webhook 入口 (仅卡片回调).
 *
 * 事件接收已改为 SDK 长连接模式 (见 FeishuLongConnectionService), 此处仅保留
 * 互动卡片按钮回调, 用于审批批准/拒绝.
 */
@Slf4j
@RestController
@RequestMapping("/webhook/feishu")
public class FeishuWebhookController {

    private final FeishuGateway gateway;
    private final ObjectMapper om;

    public FeishuWebhookController(FeishuGateway gateway, ObjectMapper om) {
        this.gateway = gateway;
        this.om = om;
    }

    /**
     * 互动卡片按钮回调 (审批批准/拒绝).
     */
    @PostMapping("/card")
    public R<Void> card(@RequestParam(required = false) String botId,
                        @RequestBody String rawBody) {
        log.info("飞书卡片回调: botId={}", botId);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = om.readValue(rawBody, Map.class);
            gateway.handleCardCallback(botId, body);
        } catch (Exception e) {
            log.error("卡片回调处理失败", e);
        }
        return R.ok();
    }
}