package cn.realm.cloud.framework.web.datacontract;

import cn.realm.cloud.framework.core.dto.PageParam;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 页码分页参数解析器。
 *
 * <p><b>职责：</b>读取请求中的 {@code pageNo} 和 {@code pageSize}，
 * 构造 {@link PageParam}。如果客户端未传参或传了非法格式，回落到
 * {@link PageParam#DEFAULT_PAGE_NO} 和 {@link PageParam#DEFAULT_PAGE_SIZE}。
 *
 * <p><b>注意：</b>这里不校验 {@code pageSize} 是否超过最大限制（如 100），
 * 这交由 {@code PageParam} 上的 {@code @Max} 或业务层（{@code BaseMapperX}）去校验。
 * 本解析器只负责“解析并组装”。
 *
 * @author QI Guang
 */
public class PageParamArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 只要 Controller 声明了 PageParam 类型的参数，就由本解析器接管
        return PageParam.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String pageNoStr = webRequest.getParameter("pageNo");
        String pageSizeStr = webRequest.getParameter("pageSize");

        int pageNo = parseOrDefault(pageNoStr, PageParam.DEFAULT_PAGE_NO);
        int pageSize = parseOrDefault(pageSizeStr, PageParam.DEFAULT_PAGE_SIZE);

        return PageParam.of(pageNo, pageSize);
    }

    private int parseOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
