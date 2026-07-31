package com.magent.platform.service.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magent.platform.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 飞书开放平台 API 封装: 消息发送 / 卡片 / Token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuClient {

    private final ObjectMapper om;
    private final RestClient restClient = RestClient.create();

    @Value("${magent.feishu.base-url:https://open.feishu.cn/open-apis}")
    private String baseUrl;

    public String getTenantToken(String appId, String appSecret) {
        try {
            String json = restClient.post()
                .uri(baseUrl + "/auth/v3/tenant_access_token/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("app_id", appId, "app_secret", appSecret))
                .retrieve()
                .body(String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> m = om.readValue(json, Map.class);
            if ((int) m.get("code") != 0) throw new BizException(500, "获取飞书 token 失败: " + m.get("msg"));
            return (String) m.get("tenant_access_token");
        } catch (BizException e) { throw e;
        } catch (Exception e) { throw new BizException(500, "飞书 token 请求失败: " + e.getMessage()); }
    }

    public void sendText(String token, String chatId, String text) {
        post(token, "/im/v1/messages", Map.of(
            "receive_id", chatId,
            "msg_type", "text",
            "content", Map.of("text", text)
        ));
    }

    public void sendRichText(String token, String chatId, String title, String elements) {
        post(token, "/im/v1/messages", Map.of(
            "receive_id", chatId,
            "msg_type", "interactive",
            "content", elements
        ));
    }

    public void sendCard(String token, String chatId, Map<String, Object> card) {
        post(token, "/im/v1/messages", Map.of(
            "receive_id", chatId,
            "msg_type", "interactive",
            "content", om.valueToTree(card).toString()
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String token, String path, Map<String, Object> body) {
        try {
            String json = restClient.post()
                .uri(baseUrl + path + "?receive_id_type=chat_id")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
            Map<String, Object> m = om.readValue(json, Map.class);
            if ((int) m.get("code") != 0) {
                log.warn("feishu api error: {}", m);
            }
            return m;
        } catch (Exception e) {
            log.error("feishu api call failed", e);
            throw new BizException(500, "飞书 API 调用失败: " + e.getMessage());
        }
    }
}