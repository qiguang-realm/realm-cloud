package cn.realm.cloud.framework.web.autoconfigure;

import cn.realm.cloud.framework.web.datacontract.ApiVersionArgumentResolver;
import cn.realm.cloud.framework.web.datacontract.PageParamArgumentResolver;
import cn.realm.cloud.framework.web.datacontract.VersionResolver;
import cn.realm.cloud.framework.web.properties.DataContractProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 数据契约自动配置（分页 + 版本化）。
 *
 * <p><b>职责：</b>装配请求参数解析器：
 * <ul>
 *     <li>页码分页：解析 {@code pageNo} 和 {@code pageSize}，自动构建并注入 {@link cn.realm.cloud.framework.core.dto.PageParam}。</li>
 *     <li>API 版本化：解析请求头或路径中的版本号，注入 Controller。</li>
 * </ul>
 *
 * @author QI Guang
 */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "realm.web.data-contract", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DataContractProperties.class)
public class DataContractAutoConfiguration {

    /**
     * 分页参数解析器。
     */
    @Bean
    @ConditionalOnMissingBean
    public PageParamArgumentResolver pageParamArgumentResolver() {
        return new PageParamArgumentResolver();
    }

    /**
     * 版本提取策略。默认从请求头 {@code X-API-Version} 提取。
     */
    @Bean
    @ConditionalOnMissingBean
    public VersionResolver versionResolver(DataContractProperties properties) {
        return new VersionResolver.HeaderVersionResolver(properties.getVersion().getHeaderName());
    }

    /**
     * 版本参数解析器。
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiVersionArgumentResolver apiVersionArgumentResolver(VersionResolver resolver) {
        return new ApiVersionArgumentResolver(resolver);
    }

    /**
     * 将自定义参数解析器注册到 Spring MVC。
     */
    @Bean
    public WebMvcConfigurer dataContractArgumentResolverConfigurer(
            PageParamArgumentResolver pageResolver,
            ApiVersionArgumentResolver versionResolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(pageResolver);
                resolvers.add(versionResolver);
            }
        };
    }
}
