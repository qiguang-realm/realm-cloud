package cn.realm.cloud.workforce.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Workforce IAM 模块的本地开发专用启动类。
 *
 * <p><b>注意：</b>本类仅存在于 {@code src/test} 目录下，用于本地开发和调试。
 * 生产环境由 {@code realm-server} 统一聚合启动，切勿将本类移到 {@code src/main} 下。
 */
@SpringBootApplication
public class LocalIdentityTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocalIdentityTestApplication.class, args);
    }
}
