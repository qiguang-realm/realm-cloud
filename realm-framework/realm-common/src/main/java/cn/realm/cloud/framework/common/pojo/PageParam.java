package cn.realm.cloud.framework.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页参数
 *
 * <p>支持普通分页和“不分页”（pageSize = -1）两种模式，适用于列表查询和导出等场景。
 *
 * @author QI Guang
 */
@Schema(description = "分页参数")
@Data
public class PageParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 默认页码
     */
    private static final Integer PAGE_NO = 1;

    /**
     * 默认每页条数
     */
    private static final Integer PAGE_SIZE = 10;

    /**
     * 每页条数 - 不分页
     * <p>设置此值时，表示查询所有数据（不分页），例如导出接口。
     */
    public static final Integer PAGE_SIZE_NONE = -1;

    /**
     * 每页条数最大值（可配置，这里硬编码为 200，实际可从配置读取）
     */
    private static final Integer MAX_PAGE_SIZE = 200;

    @Schema(description = "页码，从 1 开始", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小值为 1")
    private Integer pageNo = PAGE_NO;

    @Schema(description = "每页条数，-1 表示不分页，最大值为 200", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "每页条数不能为空")
    @Min(value = -1, message = "每页条数最小值为 -1")
    @Max(value = 200, message = "每页条数最大值为 200")
    private Integer pageSize = PAGE_SIZE;

    /**
     * 是否不分页（即查询所有数据）
     */
    public boolean isUnlimited() {
        return PAGE_SIZE_NONE.equals(pageSize);
    }

    /**
     * 获取 SQL 偏移量（用于 LIMIT offset, size）
     *
     * @return 偏移量，不分页时返回 0
     */
    public long getOffset() {
        if (isUnlimited()) {
            return 0L;
        }
        // 使用 long 计算，避免 (pageNo - 1) * pageSize 溢出 int 范围
        return (long) (pageNo - 1) * pageSize;
    }

    // 可选：提供与 MyBatis-Plus Page 对象的转换（若项目使用 MyBatis-Plus，建议放在独立工具类中）
    // 此处仅展示内部方法，避免引入依赖
}
