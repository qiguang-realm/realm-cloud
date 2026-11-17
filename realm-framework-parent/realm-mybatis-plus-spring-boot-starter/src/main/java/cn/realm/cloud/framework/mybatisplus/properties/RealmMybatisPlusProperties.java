package cn.realm.cloud.framework.mybatisplus.properties;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyBatis-Plus 的 Realm 专属配置。
 *
 * <p>不重复定义 {@code mybatis-plus.*} 的已有属性；只做 Realm 的开关和诊断选项。
 *
 * @author QI Guang
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "realm.mybatis-plus")
public class RealmMybatisPlusProperties {

    /**
     * 是否启用 Realm 的 MyBatis-Plus 适配。默认启用。
     */
    private boolean enabled = true;

    /**
     * 分页方言。为空时由 MyBatis-Plus 自动检测；多方言或多数据源场景应显式指定。
     */
    private DbType dbType;
}
