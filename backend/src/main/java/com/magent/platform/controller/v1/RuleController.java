package com.magent.platform.controller.v1;

import com.magent.platform.entity.OrchestrationRule;
import com.magent.platform.mapper.OrchestrationRuleMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController extends CrudController<OrchestrationRule, OrchestrationRuleMapper> {

    @Override
    protected Class<OrchestrationRule> entityClass() { return OrchestrationRule.class; }
}