package cn.realm.cloud.framework.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一 API 响应体（遗留备用）。
 *
 * <p><b>⚠️ 定位说明：</b>
 * 本项目默认采用 <b>Direct Resource</b>（直接返回数据） + <b>RFC 9457 (ProblemDetail)</b>（错误响应）架构。
 * 本类<b>不再作为标准返回格式</b>，仅作为遗留系统对接或特殊场景的备用载体保留。
 *
 * <p><b>新代码建议：</b>
 * <ul>
 *     <li>成功响应：直接返回业务对象（如 {@code UserDto}）。</li>
 *     <li>错误响应：抛出 {@code BusinessException}，由全局异常处理器处理。</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 * @author QI Guang
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码。
     */
    private int code;

    /**
     * 提示信息。
     */
    private String message;

    /**
     * 业务数据。
     */
    private T data;

    /**
     * 响应时间戳（epoch 毫秒，UTC）。
     */
    private long timestamp;

    /**
     * 全链路追踪 ID。
     */
    private String traceId;
}
