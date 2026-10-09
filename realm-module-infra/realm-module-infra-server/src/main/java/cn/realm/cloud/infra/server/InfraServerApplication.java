package cn.realm.cloud.infra.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 基础设施模块启动类 / Infrastructure module bootstrap class.
 *
 * @author QI Guang
 */
@SpringBootApplication
public class InfraServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfraServerApplication.class, args);
    }

}
