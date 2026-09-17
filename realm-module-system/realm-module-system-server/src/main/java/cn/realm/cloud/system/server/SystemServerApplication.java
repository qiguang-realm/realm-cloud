package cn.realm.cloud.system.server;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author QI Guang
 */
@Slf4j
@SpringBootApplication
@MapperScan("cn.realm.cloud.**.mapper")   // 通配符扫描："cn.realm.cloud.**.mapper"（扫描所有子包下的 mapper 包）
public class SystemServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemServerApplication.class, args);
        log.info("(*^▽^*)启动成功!!!(〃'▽'〃)");
    }

}
