package com.magent.platform.controller.v1;

import com.magent.platform.entity.FeishuBot;
import com.magent.platform.mapper.FeishuBotMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bots")
public class BotController extends CrudController<FeishuBot, FeishuBotMapper> {

    @Override
    protected Class<FeishuBot> entityClass() { return FeishuBot.class; }
}