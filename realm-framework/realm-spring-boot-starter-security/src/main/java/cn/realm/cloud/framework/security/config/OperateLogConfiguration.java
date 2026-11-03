package cn.realm.cloud.framework.security.config;

import cn.realm.cloud.framework.security.operatelog.aspect.OperateLogAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 操作日志配置。
 *
 * <p>依据：ASVS V7 要求所有认证决策记录日志，确保操作日志包含"谁"在"什么时间"执行了操作。
 *
 * @author QI Guang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class OperateLogConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OperateLogAspect operateLogAspect() {
        return new OperateLogAspect();
    }
}
