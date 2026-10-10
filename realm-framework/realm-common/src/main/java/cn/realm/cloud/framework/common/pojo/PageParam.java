package cn.realm.cloud.framework.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 分页请求参数（框架无关）。
 * <p>字段命名保持中性，可同时映射到：
 * <ul>
 *     <li>MyBatis-Plus 的 {@code Page(current, size)}</li>
 *     <li>Spring Data JPA 的 {@code PageRequest.of(pageNum - 1, pageSize)}</li>
 * </ul>
 * <p>本类位于 realm-common，不依赖任何持久层框架；具体转换方法由
 * {@code realm-spring-boot-starter-mybatis} 和 {@code realm-spring-boot-starter-data-jpa} 分别提供。
 *
 * @author QI Guang
 */
@Data
@NoArgsConstructor
@Schema(description = "分页请求参数")
public class PageParam implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------- 常量定义 ----------

    /**
     * 默认页码（从 1 开始）
     */
    public static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 默认每页条数
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大每页条数（防止恶意请求导致性能问题）
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 不分页标识：当 pageSize 等于此值时，表示查询所有数据（不分页）。
     * <p>请谨慎使用，仅适用于导出、下拉选项等确实需要全量数据的场景。
     */
    public static final int PAGE_SIZE_NONE = -1;

    // ---------- 字段 ----------

    /**
     * 当前页码（从 1 开始）。
     * <p>转换为 Spring Data JPA 的 {@code PageRequest} 时需减 1（Spring Data 从 0 开始）。
     */
    @Schema(description = "页码，从 1 开始",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @Min(value = 1, message = "页码最小值为 1")
    private int pageNum = DEFAULT_PAGE_NUM;

    /**
     * 每页条数。
     * <p>当值为 {@value #PAGE_SIZE_NONE} 时表示不分页。
     */
    @Schema(description = "每页条数，-1 表示不分页（查询全部，慎用），最大 100",
            example = "10",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @Min(value = PAGE_SIZE_NONE, message = "每页条数最小值为 " + PAGE_SIZE_NONE)
    @Max(value = MAX_PAGE_SIZE, message = "每页条数最大值为 " + MAX_PAGE_SIZE)
    private int pageSize = DEFAULT_PAGE_SIZE;

    // ---------- 工厂方法 ----------

    /**
     * 快速构建一个分页请求。
     */
    public static PageParam of(int pageNum, int pageSize) {
        PageParam param = new PageParam();
        param.setPageNum(pageNum);
        param.setPageSize(pageSize);
        return param;
    }

    /**
     * 返回一个不分页的请求（pageSize = -1）。
     */
    public static PageParam none() {
        return of(DEFAULT_PAGE_NUM, PAGE_SIZE_NONE);
    }

    // ---------- 便捷方法 ----------

    /**
     * 判断当前请求是否启用分页。
     */
    public boolean isPaged() {
        return this.pageSize > 0;
    }

    /**
     * 获取 SQL 偏移量（从第几条开始）。
     * <p>公式：(pageNum - 1) * pageSize
     */
    public long getOffset() {
        if (!isPaged()) {
            return 0L;
        }
        return (long) (pageNum - 1) * pageSize;
    }

    /**
     * 获取实际有效的 pageSize（若不分页则返回 0）。
     */
    public int getEffectivePageSize() {
        return isPaged() ? pageSize : 0;
    }
}
