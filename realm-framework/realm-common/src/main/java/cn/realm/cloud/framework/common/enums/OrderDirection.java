package cn.realm.cloud.framework.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 通用排序方向枚举
 *
 * @author QI Guang
 */
@Getter
public enum OrderDirection {

    ASC("ASC", "升序"),
    DESC("DESC", "降序");

    private final String code;
    private final String desc;

    OrderDirection(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取枚举值（用于序列化）
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 根据代码字符串获取枚举（用于反序列化）
     *
     * @param code 代码，如 "ASC" 或 "DESC"（不区分大小写）
     * @return 对应的枚举，若未匹配则返回 null
     */
    @JsonCreator
    public static OrderDirection fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderDirection direction : values()) {
            if (direction.code.equalsIgnoreCase(code)) {
                return direction;
            }
        }
        return null;
    }
}
