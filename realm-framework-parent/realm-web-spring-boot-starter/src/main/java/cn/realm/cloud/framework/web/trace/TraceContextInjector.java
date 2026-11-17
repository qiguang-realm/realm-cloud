package cn.realm.cloud.framework.web.trace;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.HashMap;
import java.util.Map;

/**
 * W3C Trace Context 出站注入器。
 *
 * <p><b>职责：</b>将当前线程的 OTel 上下文注入到 carrier（如 HTTP Header Map）中。
 * 供 realm-openfeign、MQ 生产者、RestClient 拦截器复用。
 *
 * @author QI Guang
 */
public class TraceContextInjector {

    private static final TextMapSetter<Map<String, String>> MAP_SETTER = (carrier, key, value) -> {
        if (carrier != null) {
            carrier.put(key, value);
        }
    };

    /**
     * 将当前上下文注入到一个新的 HTTP Header Map 中。
     *
     * @return 包含 traceparent / tracestate 的 Map，若未启用 OTel 则返回空 Map
     */
    public Map<String, String> injectToMap() {
        Map<String, String> carrier = new HashMap<>();
        inject(carrier);
        return carrier;
    }

    /**
     * 将当前上下文注入到已有的 carrier 中。
     *
     * @param carrier 目标载体，通常是 HTTP Header Map 或消息属性 Map
     */
    public void inject(Map<String, String> carrier) {
        GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(Context.current(), carrier, MAP_SETTER);
    }
}
