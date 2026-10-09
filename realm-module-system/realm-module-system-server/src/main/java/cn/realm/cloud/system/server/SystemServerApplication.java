package cn.realm.cloud.system.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * System 模块启动类 / System module bootstrap class.
 * <p>
 * 说明：Mapper 扫描统一放在 MybatisPlusConfig 的 @MapperScan 上，这里不重复配置。
 *
 * @author QI Guang
 */
@Slf4j
@SpringBootApplication
public class SystemServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemServerApplication.class, args);
        log.info("(*^▽^*)启动成功!!!(〃'▽'〃)");
    }

}
