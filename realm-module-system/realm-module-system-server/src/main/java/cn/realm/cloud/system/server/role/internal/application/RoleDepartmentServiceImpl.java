package cn.realm.cloud.system.server.role.internal.application;

import cn.realm.cloud.system.server.organization.OrganizationFacade;
import cn.realm.cloud.system.server.role.internal.persistence.RoleDO;
import cn.realm.cloud.system.server.role.internal.persistence.RoleDepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

/**
 * {@link RoleDepartmentService} 的默认实现。
 *
 * <p>数据访问委托给 {@link RoleDepartmentRepository}，部门存在性校验
 * 委托给 {@link OrganizationFacade}。本类不直接依赖 MyBatis-Plus，
 * 也不直接查询 {@code iam_department} 表——那会越过
 * {@code organization} 模块的数据所有权。
 *
 * <p><b>跨模块依赖方向：</b>本类通过 {@code OrganizationFacade.existsDepartments}
 * <b>同步</b>校验部门有效性（{@code role → organization}），
 * {@code organization} 模块通过发布 {@code DepartmentDeletedEvent}
 * 通知关联清理（{@code organization → events → role}）。
 * 两个方向同时存在不构成循环——{@code organization} 不反向同步依赖
 * {@code role} 的内部类型，也不查询 {@code iam_role_department} 表。
 *
 * <p><b>为什么 {@code dataScope} 校验在 Service 而不在 DTO：</b>
 * {@code RoleDepartmentAssignRequest} 只包含 {@code departmentIds}，
 * 拿不到角色的当前 {@code dataScope}。判断“非自定义范围下拒绝写入”
 * 需要查库，属于用例层的业务规则，不在 DTO 层做。
 *
 * @author QI Guang
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class RoleDepartmentServiceImpl implements RoleDepartmentService {

    private final RoleService roleService;
    private final RoleDepartmentRepository roleDepartmentRepository;
    private final OrganizationFacade organizationFacade;

    /**
     * {@inheritDoc}
     *
     * <p><b>实现说明：</b>先查询角色，如果角色不存在或 {@code dataScope}
     * 不是自定义，直接返回空列表——查询接口对“不存在的关联”返回空，
     * 而不是抛异常。这与 {@code replaceRoleDepartments} 的严格校验不同：
     * 读操作宽容，写操作严格。
     */
    @Override
    public List<Long> findDepartmentIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        RoleDO role = roleService.findById(roleId).orElse(null);
        if (role == null || !Objects.equals(role.getDataScope(), RoleDataScope.CUSTOM)) {
            return List.of();
        }
        return roleDepartmentRepository.findDepartmentIdsByRoleId(roleId);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>实现步骤：</b>
     * <ol>
     *   <li>校验角色存在（不存在时 {@code getRole} 抛异常）</li>
     *   <li>校验 {@code dataScope = 2}（自定义）</li>
     *   <li>去重 {@code departmentIds}，避免撞唯一索引</li>
     *   <li>校验部门 ID 有效性（通过 {@code OrganizationFacade.existsDepartments}）</li>
     *   <li>全量替换：先删后插，同一事务内完成</li>
     * </ol>
     *
     * <p><b>校验顺序的理由：</b>先校验角色和 {@code dataScope}（最轻的本地查询），
     * 再校验部门 ID（跨模块调用，最重的操作），最后写入。这样无效请求
     * 尽早失败，不浪费一次跨模块查询。
     */
    @Override
    @Transactional
    public void replaceRoleDepartments(Long roleId, List<Long> departmentIds) {
        // 1. 校验角色存在
        RoleDO role = roleService.getRole(roleId);

        // 2. 校验 dataScope 必须为自定义
        if (!Objects.equals(role.getDataScope(), RoleDataScope.CUSTOM)) {
            throw new IllegalStateException(
                    "角色数据范围不是自定义，无法设置部门列表: roleId=" + roleId
                            + ", dataScope=" + role.getDataScope());
        }

        // 3. 去重；null 视为空列表
        List<Long> distinctIds = (departmentIds == null || departmentIds.isEmpty())
                ? List.of()
                : departmentIds.stream().distinct().toList();

        // 4. 校验部门有效性——空列表跳过（清空场景无需校验）
        if (!distinctIds.isEmpty()
                && !organizationFacade.existsByIds(distinctIds)) {
            throw new IllegalArgumentException(
                    "部门 ID 列表中存在无效部门: " + distinctIds);
        }

        // 5. 全量替换
        roleDepartmentRepository.replaceByRoleId(roleId, distinctIds);
        log.info("替换角色自定义数据范围部门, roleId={}, departmentCount={}",
                roleId, distinctIds.size());
    }
}
