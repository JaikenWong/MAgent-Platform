package com.magent.platform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagentApplicationTests {

    @Test
    void smokeTest() {
        // Phase 0: 留空骨架. Phase 1+ 用 Testcontainers + Postgres 做集成测试.
        assertTrue(true, "smoke");
    }
}