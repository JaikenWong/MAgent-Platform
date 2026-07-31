package com.magent.platform.service.feishu;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import com.magent.platform.common.CryptoUtil;
import com.magent.platform.entity.FeishuBot;
import com.magent.platform.mapper.FeishuBotMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 飞书长连接服务: 使用官方 SDK 的 ws.Client, 无需公网 webhook.
 *
 * 每个 active 的 FeishuBot 维持一条 SDK 管理的 WebSocket 连接:
 *   1. EventDispatcher 注册 im.message.receive_v1 事件处理
 *   2. Client.Builder(appId, appSecret).eventHandler(dispatcher).build()
 *   3. client.start() 开启 WebSocket (SDK 自动心跳+重连)
 *   4. 收到消息 → 转发到 FeishuGateway → Orchestrator
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuLongConnectionService {

    private final FeishuBotMapper botMapper;
    private final FeishuGateway gateway;
    private final ObjectMapper om;

    private final Map<String, Client> clientMap = new ConcurrentHashMap<>();
    private final ExecutorService relayExecutor = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "feishu-relay");
        t.setDaemon(true);
        return t;
    });

    /**
     * 应用启动后自动恢复所有 active bot 的长连接.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreOnStartup() {
        List<FeishuBot> bots = botMapper.selectList(
            new QueryWrapper<FeishuBot>()
                .eq("long_connection_enabled", true)
                .eq("status", "active"));
        for (FeishuBot bot : bots) {
            try {
                enable(bot);
            } catch (Exception e) {
                log.error("启动长连接失败: bot={}", bot.getName(), e);
            }
        }
        log.info("飞书长连接已启动: {} 个 bot 在线", clientMap.size());
    }

    /**
     * 为一个 Bot 开启长连接 (按 ID).
     */
    public void enable(String botId) {
        FeishuBot bot = botMapper.selectById(botId);
        if (bot == null) {
            log.warn("bot 不存在: {}", botId);
            return;
        }
        enable(bot);
    }

    /**
     * 为一个 Bot 开启长连接.
     */
    public void enable(FeishuBot bot) {
        String botId = bot.getId();
        if (clientMap.containsKey(botId)) {
            log.info("长连接已存在, 跳过: bot={}", bot.getName());
            return;
        }

        String appSecret = decryptSecret(bot.getAppSecret());
        String verificationToken = decryptSecret(bot.getVerificationToken());
        if (verificationToken == null) verificationToken = "";
        String encryptKey = decryptSecret(bot.getEncryptKey());
        if (encryptKey == null) encryptKey = "";

        // 1. 构建事件分发器
        final FeishuBot botRef = bot;
        EventDispatcher dispatcher = EventDispatcher.newBuilder(verificationToken, encryptKey)
            .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                @Override
                public void handle(P2MessageReceiveV1 event) {
                    // 异步处理, 不阻塞 SDK 的 dispatch 线程
                    relayExecutor.submit(() -> {
                        try {
                            gateway.handleMessageEvent(botRef, event);
                        } catch (Exception e) {
                            log.error("消息处理失败: bot={}", botRef.getName(), e);
                        }
                    });
                }
            })
            .build();

        // 2. 构建 SDK WS Client
        Client client = new Client.Builder(bot.getAppId(), appSecret)
            .eventHandler(dispatcher)
            .build();
        client.start();

        clientMap.put(botId, client);
        log.info("飞书长连接已开启: bot={}", bot.getName());
    }

    /**
     * 关闭一个 Bot 的长连接.
     */
    public void disable(String botId) {
        Client client = clientMap.remove(botId);
        if (client != null) {
            shutdownClient(client);
            log.info("飞书长连接已关闭: botId={}", botId);
        }
    }

    public int activeCount() {
        return clientMap.size();
    }

    @PreDestroy
    public void shutdown() {
        clientMap.forEach((botId, client) -> shutdownClient(client));
        clientMap.clear();
        relayExecutor.shutdown();
        log.info("飞书长连接服务已关闭");
    }

    /**
     * SDK 的 Client 没有公开 stop 方法, 用反射关闭.
     */
    private void shutdownClient(Client client) {
        try {
            Field autoReconnect = Client.class.getDeclaredField("autoReconnect");
            autoReconnect.setAccessible(true);
            autoReconnect.set(client, false);

            Method disconnect = Client.class.getDeclaredMethod("disconnect");
            disconnect.setAccessible(true);
            disconnect.invoke(client);

            Field executorField = Client.class.getDeclaredField("executor");
            executorField.setAccessible(true);
            ExecutorService executor = (ExecutorService) executorField.get(client);
            if (executor != null) executor.shutdownNow();
        } catch (ReflectiveOperationException e) {
            log.warn("反射关闭 Client 失败 (SDK 升级后可能需调整): {}", e.getMessage());
        }
    }

    private String decryptSecret(String secret) {
        if (secret == null || secret.isBlank()) return null;
        try {
            return CryptoUtil.decrypt(secret);
        } catch (Exception e) {
            return secret;
        }
    }
}