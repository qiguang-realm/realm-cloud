package cn.realm.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Realm 模块化单体的唯一启动入口。
 * <p>
 * 注意：本类必须位于 {@code cn.realm.cloud} 根包下。
 * 因为 Spring Modulith 会将本类所在包的直接子包（如 customer, payment, workforce 等）
 * 自动识别为独立的业务模块。如果本类下沉到 cn.realm.cloud.server，将导致
 * Modulith 无法正确识别同级的业务模块。
 *
 * @author QI Guang
 */
@SpringBootApplication
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
