package com.magent.platform.controller.v1;

import com.magent.platform.common.BizException;
import com.magent.platform.common.CryptoUtil;
import com.magent.platform.common.R;
import com.magent.platform.dto.a2a.AgentCard;
import com.magent.platform.entity.Agent;
import com.magent.platform.mapper.AgentMapper;
import com.magent.platform.service.a2a.AgentCardService;
import com.magent.platform.service.dify.DifyBlockingResult;
import com.magent.platform.service.dify.DifyClient;
import com.magent.platform.service.dify.DifyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController extends CrudController<Agent, AgentMapper> {

    private final AgentCardService cardService;
    private final DifyClient difyClient;

    @Value("${magent.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Override
    protected Class<Agent> entityClass() { return Agent.class; }

    /** Agent Card 预览 (按数据库配置生成, 不走公开 A2A endpoint). */
    @GetMapping("/{id}/card")
    public R<AgentCard> card(@PathVariable String id) {
        Agent a = mapper.selectById(id);
        if (a == null) throw new BizException(404, "agent not found");
        return R.ok(cardService.build(a, publicBaseUrl));
    }

    /** 测试连通: 用一个简单 "hello" 调一次 Dify, 返回 answer / outputs / err. */
    @PostMapping("/{id}/test")
    public R<Map<String, Object>> test(@PathVariable String id) {
        Agent a = mapper.selectById(id);
        if (a == null) throw new BizException(404, "agent not found");
        if (a.getDifyApiKey() == null || a.getDifyApiKey().isBlank())
            throw new BizException(400, "agent dify_api_key not configured");
        String apiKey;
        try {
            apiKey = CryptoUtil.decrypt(a.getDifyApiKey());
        } catch (Exception e) {
            apiKey = a.getDifyApiKey();
        }
        String base = (a.getDifyBaseUrl() == null || a.getDifyBaseUrl().isBlank())
            ? difyClient.defaultBase() : a.getDifyBaseUrl();

        boolean isChat = a.getDifyAppId() != null && a.getDifyAppId().startsWith("chat-");
        DifyRequest req = isChat
            ? new DifyRequest(null, "ping", "test-user", "", null, "blocking")
            : new DifyRequest(Map.of("query", "ping").toString(), null, "test-user", null, null, "blocking");
        try {
            DifyBlockingResult res = difyClient.blocking(base, apiKey, req);
            return R.ok(Map.of(
                "ok", true,
                "answer", res.answer() == null ? "" : res.answer(),
                "outputs", res.outputs() == null ? Map.of() : res.outputs(),
                "workflow_run_id", res.workflowRunId() == null ? "" : res.workflowRunId(),
                "conversation_id", res.conversationId() == null ? "" : res.conversationId()
            ));
        } catch (BizException e) {
            return R.ok(Map.of("ok", false, "error", e.getMessage(), "code", e.getCode()));
        }
    }

    /**
     * 创建 Agent 时自动 AES 加密 difyApiKey (若不是已加密格式).
     */
    @Override
    @PostMapping
    public R<Agent> create(@Valid @RequestBody Agent entity) {
        encryptApiKeyIfNecessary(entity);
        mapper.insert(entity);
        return R.ok(entity);
    }

    @Override
    @PutMapping("/{id}")
    public R<Agent> update(@PathVariable String id, @RequestBody Agent entity) {
        entity.setId(id);
        Agent db = mapper.selectById(id);
        if (db != null && (entity.getDifyApiKey() == null || entity.getDifyApiKey().isBlank())) {
            entity.setDifyApiKey(db.getDifyApiKey());
        } else {
            encryptApiKeyIfNecessary(entity);
        }
        mapper.updateById(entity);
        return R.ok(entity);
    }

    private void encryptApiKeyIfNecessary(Agent a) {
        if (a.getDifyApiKey() == null || a.getDifyApiKey().isBlank()) return;
        try {
            CryptoUtil.decrypt(a.getDifyApiKey());   // 已是密文: 不再加密
        } catch (Exception e) {
            // 明文 → 加密
            a.setDifyApiKey(CryptoUtil.encrypt(a.getDifyApiKey()));
        }
    }
}