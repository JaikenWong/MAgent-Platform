package com.magent.platform.controller.webhook;

import com.magent.platform.common.R;
import com.magent.platform.service.feishu.FeishuGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 飞书事件入口.
 *  - GET  /webhook/feishu/{botId} : URL 校验 (challenge)
 *  - POST /webhook/feishu/{botId} : 消息/事件回调
 *  - POST /webhook/feishu/card     : 互动卡片按钮回调 (审批)
 */
@Slf4j
@RestController
@RequestMapping("/webhook/feishu")
public class FeishuWebhookController {

    private final FeishuGateway gateway;

    public FeishuWebhookController(FeishuGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/{botId}")
    public Object verify(@PathVariable String botId,
                         @RequestParam(required = false) String challenge,
                         @RequestBody(required = false) Map<String, Object> body) {
        log.info("feishu verify botId={}", botId);
        if (body != null) {
            String resp = gateway.verifyUrl(botId, body);
            if (resp != null) return Map.of("challenge", resp);
        }
        return Map.of("challenge", challenge == null ? "" : challenge);
    }

    @PostMapping("/{botId}")
    public R<Void> event(@PathVariable String botId, @RequestBody Map<String, Object> body) {
        log.info("feishu event botId={}", botId);
        gateway.handleEvent(botId, body);
        return R.ok();
    }

    @PostMapping("/card")
    public R<Void> card(@RequestParam(required = false) String botId,
                        @RequestBody Map<String, Object> body) {
        log.info("feishu card callback botId={}", botId);
        gateway.handleCardCallback(botId, body);
        return R.ok();
    }
}