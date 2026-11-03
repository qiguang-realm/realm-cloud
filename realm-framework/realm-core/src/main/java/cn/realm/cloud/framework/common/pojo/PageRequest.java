package cn.realm.cloud.framework.common.pojo;

/**
 * 分页请求契约。
 *
 * <p>所有“分页查询请求 DTO”都实现本接口，统一提供分页参数的兜底逻辑
 * 和向 {@link PageParam} 的转换——避免每个 DTO 重复实现“null 归一为默认值”
 * 和“剥离分页部分”这两段代码。
 *
 * <p><b>兜底语义：</b>{@code pageNo} / {@code pageSize} 为 {@code null}
 * 或小于下限时，{@link #toPageParam()} 归一为
 * {@link PageParam#DEFAULT_PAGE_NO} / {@link PageParam#DEFAULT_PAGE_SIZE}。
 * 这是“分页参数是可选的、未传或传错都按默认值处理”的宽容策略——
 * 用户不会因为忘记传 {@code pageNo} 而拿到 400。
 *
 * <p><b>与 {@code @Min} / {@code @Max} 的关系：</b>{@code @Min(1)} 实际上
 * 永远不会触发（因为 {@code toPageParam()} 已兜底为 {@code 1}），
 * 但保留它是“意图声明”——告诉读者“这里不允许负数”。
 * {@code @Max(100)} 会触发（{@code pageSize = 200} 时校验失败返回 400）——
 * “过大”是应当被明确拒绝的非法输入，不是应当被静默修正的缺省值。
 *
 * <p><b>为什么不在 DTO 的紧凑构造器里兜底：</b>紧凑构造器在每个 DTO 里
 * 都要重复一遍，且兜底后的值只能被该 DTO 的访问器读到——无法被
 * {@link PageParam} 层复用。放在接口的 default 方法里，兜底逻辑只有一处，
 * 未来默认值调整时只改 {@link PageParam} 的常量，所有实现类自动跟上。
 *
 * <p><b>命名提示：</b>本接口与 Spring Data 的
 * {@code org.springframework.data.domain.PageRequest} 同名。如果某个类
 * 同时需要两者，用全限定名区分。业务代码通常只用本接口，不会冲突。
 *
 * @author QI Guang
 */
public interface PageRequest {

    /**
     * 页码，从 1 开始。为 {@code null} 时由 {@link #toPageParam()} 兜底。
     */
    Integer pageNo();

    /**
     * 每页条数，最大 100。为 {@code null} 时由 {@link #toPageParam()} 兜底。
     */
    Integer pageSize();

    /**
     * 把分页部分剥离为框架层的 {@link PageParam}。
     *
     * <p>{@code pageNo} / {@code pageSize} 为 {@code null} 或小于下限时，
     * 归一为默认值。这样可以安全地拆箱为 {@code int} 传给
     * {@link PageParam#of(int, int)}，不会 NPE。
     *
     * <p>由于本方法已经兜底，实现类<b>不需要</b>在紧凑构造器里再做一次
     * 兜底——两次兜底是重复逻辑，会让“默认值到底在哪一层生效”变得模糊。
     *
     * <p><b>超大值不在此处修正：</b>{@code pageSize > 100} 时本方法不截断，
     * 而是原样传给 {@link PageParam}，由 Service 层显式校验并抛
     * {@link IllegalArgumentException}。这是“超大值应被明确拒绝”的语义——
     * 而不是静默截断为 100 让调用方误以为拿到了完整结果。
     *
     * @return 分页参数
     */
    default PageParam toPageParam() {
        int no = pageNo() == null || pageNo() < 1
                ? PageParam.DEFAULT_PAGE_NO
                : pageNo();
        int size = pageSize() == null || pageSize() < 1
                ? PageParam.DEFAULT_PAGE_SIZE
                : pageSize();
        return PageParam.of(no, size);
    }
}
