package cn.realm.cloud.framework.web.datacontract;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 注入 API 版本号的参数解析器。
 *
 * <p>配合 {@link ApiVersion} 注解使用，如：
 * <pre>{@code
 * @GetMapping("/users")
 * public List<User> getUsers(@ApiVersion String version) { ... }
 * }</pre>
 *
 * @author QI Guang
 */
public class ApiVersionArgumentResolver implements HandlerMethodArgumentResolver {

    private final VersionResolver resolver;

    public ApiVersionArgumentResolver(VersionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == String.class
                && parameter.hasParameterAnnotation(ApiVersion.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return request != null ? resolver.resolve(request) : null;
    }
}
