package cn.realm.cloud.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Realm 后端一体化服务启动类 / Realm Server bootstrap class.
 * <p>
 * 通过聚合 realm-module-*-server 模块构建完整应用。
 * Aggregates realm-module-*-server modules into a complete application.
 *
 * @author QI Guang
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "cn.realm.cloud")
public class RealmServerApplication {

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        SpringApplication.run(RealmServerApplication.class, args);
        log.info("(๑•̀ㅂ•́)و✧ Realm 启动成功！耗时 {} ms", System.currentTimeMillis() - start);
    }

}
