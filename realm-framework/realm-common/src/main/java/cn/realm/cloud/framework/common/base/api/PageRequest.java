package cn.realm.cloud.framework.common.base.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 分页请求参数（用于接收前端分页参数）
 * <p>包含页码、每页条数，并提供不分页标志（pageSize = -1）。
 * 支持 JSR-303 校验，确保参数合法。
 *
 * @author QI Guang
 */
@Data
@NoArgsConstructor  // 供反序列化使用
public class PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------- 常量定义 ----------

    /**
     * 默认页码（从1开始）
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
     * 不分页标识：当 pageSize 等于此值时，表示查询所有数据（不分页）
     */
    public static final int PAGE_SIZE_NONE = -1;

    // ---------- 字段 ----------

    /**
     * 当前页码（默认 1）
     */
    @Min(value = 1, message = "页码最小值为 1")
    private int pageNum = DEFAULT_PAGE_NUM;

    /**
     * 每页条数（默认 10）
     * <p>当值为 {@value #PAGE_SIZE_NONE} 时表示不分页。
     */
    @Min(value = PAGE_SIZE_NONE, message = "每页条数最小值为 " + PAGE_SIZE_NONE)
    @Max(value = MAX_PAGE_SIZE, message = "每页条数最大值为 " + MAX_PAGE_SIZE)
    private int pageSize = DEFAULT_PAGE_SIZE;

    // ---------- 工厂方法（可选） ----------

    /**
     * 快速构建一个分页请求
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return PageRequest 对象
     */
    public static PageRequest of(int pageNum, int pageSize) {
        PageRequest request = new PageRequest();
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        return request;
    }

    /**
     * 返回一个不分页的请求（pageSize = -1）
     *
     * @return 不分页的请求
     */
    public static PageRequest none() {
        PageRequest request = new PageRequest();
        request.setPageNum(DEFAULT_PAGE_NUM);
        request.setPageSize(PAGE_SIZE_NONE);
        return request;
    }

    // ---------- 便捷方法 ----------

    /**
     * 判断当前请求是否启用分页
     *
     * @return true 表示启用分页（pageSize > 0），false 表示不分页
     */
    public boolean isPaged() {
        return this.pageSize > 0;
    }

    /**
     * 获取 MyBatis 分页的 offset（从第几条开始）
     * <p>公式：(pageNum - 1) * pageSize
     * <p>注意：仅当 {@link #isPaged()} 为 true 时有效，否则返回 0。
     *
     * @return offset 值
     */
    public long getOffset() {
        if (!isPaged()) {
            return 0;
        }
        return (long) (pageNum - 1) * pageSize;
    }

    /**
     * 获取实际有效的 pageSize（若不分页则返回 0，便于底层处理）
     *
     * @return 有效的 pageSize，若不分页则返回 0
     */
    public int getEffectivePageSize() {
        return isPaged() ? pageSize : 0;
    }
}
