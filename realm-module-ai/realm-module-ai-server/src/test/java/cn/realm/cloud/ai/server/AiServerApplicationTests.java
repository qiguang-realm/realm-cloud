package cn.realm.cloud.ai.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link AiServerApplication} 的 Spring 应用上下文加载测试。
 *
 * <p>用于验证 AI 模块在测试环境下能够正常启动。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AiServerApplicationTests {

    /**
     * 上下文加载冒烟测试。
     */
    @Test
    void contextLoads() {
    }
}
