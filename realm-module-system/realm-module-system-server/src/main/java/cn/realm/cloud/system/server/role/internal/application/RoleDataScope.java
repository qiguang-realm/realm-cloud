package cn.realm.cloud.system.server.role.internal.application;

/**
 * 角色的数据范围常量。
 *
 * <p>与 {@code RoleDO.dataScope} 的取值一一对应。使用常量替代
 * 魔法数字（{@code dataScope == 2}），让业务代码表达意图而非数值——
 * {@code dataScope == RoleDataScope.CUSTOM} 读起来比 {@code == 2} 明确得多。
 *
 * <p>放在 {@code application} 包而非 {@code persistence}：数据范围是
 * 业务语义，Service 层需要引用它；放在 {@code application} 让
 * {@code RoleServiceImpl} 不必反向依赖 {@code persistence} 中的常量类。
 *
 * @author QI Guang
 */
public final class RoleDataScope {

    /**
     * 全部数据
     */
    public static final int ALL = 1;

    /**
     * 自定义部门
     */
    public static final int CUSTOM = 2;

    /**
     * 本部门
     */
    public static final int DEPT = 3;

    /**
     * 本部门及以下
     */
    public static final int DEPT_AND_CHILD = 4;

    /**
     * 仅本人
     */
    public static final int SELF = 5;

    private RoleDataScope() {
    }
}
