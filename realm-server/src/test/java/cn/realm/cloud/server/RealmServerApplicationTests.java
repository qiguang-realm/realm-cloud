package cn.realm.cloud.server;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link RealmServerApplication} 的集成冒烟测试。
 *
 * <p>realm-server 是聚合启动容器，本测试验证所有已启用的
 * realm-module-*-server 模块能被正确拼装并启动。
 *
 * <p>标签 {@code integration}：默认不参与 {@code mvn test}，
 * 由 CI 单独触发，避免拖慢日常开发。
 *
 * @author QI Guang
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RealmServerApplicationTests {

    /**
     * 聚合上下文加载冒烟测试。
     */
    @Test
    void contextLoads() {
    }
}
