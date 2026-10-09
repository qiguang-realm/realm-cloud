package cn.realm.cloud.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Realm 网关启动类 / Realm Gateway bootstrap class.
 * <p>
 * 基于 Spring Cloud Gateway 实现路由转发、流量控制和安全防护。
 * Built on Spring Cloud Gateway for routing, traffic control, and security protection.
 *
 * @author QI Guang
 */
@SpringBootApplication
public class GatewayServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServerApplication.class, args);
    }
}
