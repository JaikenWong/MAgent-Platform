package com.magent.platform.controller.webhook;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.CryptoUtil;
import com.magent.platform.common.BizException;
import com.magent.platform.common.R;
import com.magent.platform.entity.FeishuBot;
import com.magent.platform.mapper.FeishuBotMapper;
import com.magent.platform.service.feishu.FeishuCryptoUtil;
import com.magent.platform.service.feishu.FeishuGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 飞书 webhook 入口 (仅卡片回调).
 *
 *  事件接收由 SDK 长连接接管 (见 FeishuLongConnectionService), 此处只处理
 *  互动卡片按钮回调 (审批批准/拒绝). 支持加密回调解密 + 签名校验.
 */
@Slf4j
@RestController
@RequestMapping("/webhook/feishu")
@RequiredArgsConstructor
public class FeishuWebhookController {

    private final FeishuGateway gateway;
    private final FeishuBotMapper botMapper;
    private final FeishuCryptoUtil cryptoUtil;
    private final ObjectMapper om;

    @PostMapping("/card")
    public R<Void> card(@RequestParam(required = false) String botId,
                        @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
                        @RequestHeader(value = "X-Lark-Request-Nonce", required = false) String nonce,
                        @RequestHeader(value = "X-Lark-Signature", required = false) String signature,
                        @RequestBody String rawBody) {
        log.info("飞书卡片回调: botId={}", botId);
        try {
            String json = rawBody;

            // 加密回调: {"encrypt":"base64..."} -> 解密
            if (rawBody.contains("\"encrypt\"")) {
                FeishuBot bot = findBot(botId);
                if (bot == null) {
                    log.warn("卡片回调无法定位 bot: {}", botId);
                    return R.ok();
                }
                String encryptKey = decryptEncryptKey(bot.getEncryptKey());
                @SuppressWarnings("unchecked")
                Map<String, Object> enc = om.readValue(rawBody, Map.class);
                json = cryptoUtil.decrypt(encryptKey, (String) enc.get("encrypt"));
            }

            // 签名校验 (有签名头即强校验, 失败拒绝)
            if (signature != null && !signature.isBlank()) {
                FeishuBot bot = findBot(botId);
                if (bot != null) {
                    String encryptKey = decryptEncryptKey(bot.getEncryptKey());
                    boolean ok = cryptoUtil.verifySignature(encryptKey, timestamp, nonce, rawBody, signature);
                    if (!ok) throw new BizException(403, "飞书签名校验失败");
                }
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = om.readValue(json, Map.class);
            gateway.handleCardCallback(botId, body);
        } catch (Exception e) {
            log.error("卡片回调处理失败", e);
        }
        return R.ok();
    }

    private FeishuBot findBot(String botId) {
        if (botId != null && !botId.isBlank()) return botMapper.selectById(botId);
        return botMapper.selectOne(
            new QueryWrapper<FeishuBot>().eq("status", "active").last("limit 1"));
    }

    private String decryptEncryptKey(String cipher) {
        if (cipher == null || cipher.isBlank()) return "";
        try { return CryptoUtil.decrypt(cipher); } catch (Exception e) { return cipher; }
    }
}
