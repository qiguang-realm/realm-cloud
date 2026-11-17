package cn.realm.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifiesModularStructure() {
        // 这一行代码，会自动去扫描 cn.realm.cloud 下的所有子模块，
        // 校验它们之间有没有违规的循环依赖、有没有越权访问 internal 包
        ApplicationModules.of(ServerApplication.class).verify();
    }
}
