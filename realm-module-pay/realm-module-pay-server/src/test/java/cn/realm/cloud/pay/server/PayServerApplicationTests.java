package cn.realm.cloud.pay.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link PayServerApplication} 的 Spring 应用上下文加载测试。
 *
 * <p>用于验证支付模块在测试环境下能够正常启动。
 *
 * @author QI Guang
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PayServerApplicationTests {

    /**
     * 上下文加载冒烟测试。
     */
    @Test
    void contextLoads() {
    }
}
