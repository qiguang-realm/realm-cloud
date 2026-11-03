package cn.realm.cloud.framework.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 分页请求参数（框架无关）。
 *
 * <p>字段命名保持中性，可同时映射到：
 * <ul>
 *     <li>MyBatis-Plus 的 {@code Page(pageNo, pageSize)}</li>
 *     <li>Spring Data JPA 的 {@code PageRequest.of(pageNo - 1, pageSize)}</li>
 * </ul>
 *
 * <p>本类位于 realm-core，不依赖任何持久层框架；具体转换方法由
 * {@code realm-spring-boot-starter-mybatis} 和
 * {@code realm-spring-boot-starter-data-jpa} 分别提供。
 *
 * <p><b>只支持分页查询，不提供“不分页”模式：</b>依据 Google AIP-158
 * 与 Microsoft REST API Guidelines——集合端点必须分页，不存在
 * “返回全量”这一选项。全量数据的需求应由独立的导出端点承担，
 * 而不是让列表查询通过 {@code pageSize = -1} 退化为全表扫描。
 * 一个没有 {@code LIMIT} 的列表端点是拒绝服务攻击的入口，
 * 也会让响应体大小随数据增长而失控。
 *
 * <p>字段采用可空包装类型：{@code null} 表示调用方未提供该参数，
 * 由 {@link #getPageNoOrDefault()} / {@link #getPageSizeOrDefault()}
 * 统一回落默认值。校验注解 {@code @Min} / {@code @Max} 对
 * {@code null} 不生效，所以“未传”和“传了非法值”由两层分别处理。
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
     * 默认页码（从 1 开始）。
     */
    public static final int DEFAULT_PAGE_NO = 1;

    /**
     * 默认每页条数。
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大每页条数。
     *
     * <p>与联合国欧洲经济委员会 OpenAPI 规范、Microsoft Graph API
     * 的默认上限（100）一致。超过此值时，HTTP 入口由 {@code @Max}
     * 拦截（返回 400），内部调用由 {@code BaseMapperX.selectPage}
     * 显式校验（抛 {@code IllegalArgumentException}），
     * {@code MybatisPlusConfig} 的 {@code setMaxLimit} 作为兜底。
     */
    public static final int MAX_PAGE_SIZE = 100;

    // ---------- 字段 ----------

    /**
     * 当前页码（从 1 开始），{@code null} 表示未提供，
     * 按 {@link #DEFAULT_PAGE_NO} 处理。
     *
     * <p>转换为 Spring Data JPA 的 {@code PageRequest} 时需减 1
     * （Spring Data 从 0 开始）。
     */
    @Schema(description = "页码，从 1 开始；不传时使用默认值 1",
            example = "1",
            defaultValue = "1",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 1, message = "页码最小值为 1")
    private Integer pageNo;

    /**
     * 每页条数，{@code null} 表示未提供，按
     * {@link #DEFAULT_PAGE_SIZE} 处理。
     *
     * <p><b>不允许 {@code <= 0}：</b>当前设计不支持“不分页”模式。
     * 传 {@code 0} 或负数会由 {@code BaseMapperX.selectPage}
     * 的显式校验拒绝，并抛 {@code IllegalArgumentException}。
     */
    @Schema(description = "每页条数，最大 100；不传时使用默认值 10",
            example = "10",
            defaultValue = "10",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Min(value = 1, message = "每页条数最小值为 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数最大值为 " + MAX_PAGE_SIZE)
    private Integer pageSize;

    // ---------- 工厂方法 ----------

    /**
     * 快速构建一个分页请求。
     *
     * <p>不校验参数范围——校验由 {@code @Min} / {@code @Max}
     * 或 {@code BaseMapperX.selectPage} 完成。工厂方法只做构造，
     * 不做业务判断。
     *
     * @param pageNo   页码，从 1 开始
     * @param pageSize 每页条数，必须 {@code > 0} 且 {@code <= MAX_PAGE_SIZE}
     */
    public static PageParam of(int pageNo, int pageSize) {
        PageParam param = new PageParam();
        param.setPageNo(pageNo);
        param.setPageSize(pageSize);
        return param;
    }

    // ---------- 便捷方法 ----------

    /**
     * 获取有效页码：{@code null} 时回落为 {@link #DEFAULT_PAGE_NO}。
     *
     * @return 页码，永远 {@code >= 1}
     */
    public int getPageNoOrDefault() {
        return this.pageNo != null ? this.pageNo : DEFAULT_PAGE_NO;
    }

    /**
     * 获取有效每页条数：{@code null} 时回落为
     * {@link #DEFAULT_PAGE_SIZE}。
     *
     * <p><b>返回值可能 {@code <= 0}：</b>如果调用方绕过
     * {@code @Valid} 直接构造 {@code PageParam.of(1, 0)}，
     * 本方法会原样返回 {@code 0}。调用方（如
     * {@code BaseMapperX.selectPage}）需要在校验后使用本方法，
     * 而不是假设它永远返回正数。
     *
     * @return 每页条数；未传时为 {@link #DEFAULT_PAGE_SIZE}，
     * 传了非法值时原样返回
     */
    public int getPageSizeOrDefault() {
        return this.pageSize != null ? this.pageSize : DEFAULT_PAGE_SIZE;
    }

    /**
     * 获取 SQL 偏移量（从第几条开始）。
     *
     * <p>公式：{@code (pageNo - 1) * pageSize}。供 Spring Data JPA
     * 或手写 SQL 的 {@code LIMIT offset, size} 使用。
     * MyBatis-Plus 的 {@code Page(pageNo, pageSize)} 内部自行处理偏移量，
     * 不需要调用本方法。
     *
     * @return 偏移量，非负
     */
    public long getOffset() {
        return (long) (getPageNoOrDefault() - 1) * getPageSizeOrDefault();
    }
}
