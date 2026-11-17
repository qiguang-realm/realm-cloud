package cn.realm.cloud.payment.exception;

import cn.realm.cloud.framework.core.exception.ErrorCode;
import lombok.Getter;

/**
 * 支付业务错误码。
 *
 * <p><b>HTTP 映射：</b>本枚举不携带 HTTP 状态。精确映射在
 * {@code PaymentErrorCodeAutoConfiguration} 中注册到
 * {@code ErrorCodeHttpStatusMapping}；未注册的码兜底 500。
 *
 * @author QI Guang
 */
@Getter
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_NOT_FOUND(2201, "支付单不存在"),
    PAYMENT_STATUS_INVALID(2202, "支付状态不允许此操作"),
    PAYMENT_INSUFFICIENT_BALANCE(2203, "账户余额不足"),
    PAYMENT_DECLINED(2204, "支付被拒绝"),
    PAYMENT_DUPLICATE(2205, "重复支付请求"),
    PAYMENT_REFUND_FAILED(2206, "退款失败"),
    PAYMENT_AMOUNT_INVALID(2207, "支付金额无效"),
    PAYMENT_CURRENCY_UNSUPPORTED(2208, "不支持的币种");

    private final int code;
    private final String message;

    PaymentErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
