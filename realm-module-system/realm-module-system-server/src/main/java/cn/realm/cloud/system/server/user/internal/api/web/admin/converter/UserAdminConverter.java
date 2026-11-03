package cn.realm.cloud.system.server.user.internal.api.web.admin.converter;

import cn.realm.cloud.framework.common.pojo.PageResult;
import cn.realm.cloud.system.server.user.internal.api.web.admin.dto.UserCreateRequest;
import cn.realm.cloud.system.server.user.internal.api.web.admin.dto.UserResponse;
import cn.realm.cloud.system.server.user.internal.api.web.admin.dto.UserUpdateRequest;
import cn.realm.cloud.system.server.user.internal.persistence.UserDO;

import java.util.List;

/**
 * 管理后台 Request/Response 与 {@link UserDO} 的转换。
 *
 * <p>只服务于 {@code internal.api.web.admin} 下的 Controller，
 * 不跨端复用——App 端的字段可见性与 Admin 端不同，共享契约会造成越权泄漏。
 *
 * <p>本类只做字段映射，不承载业务编排；取数、校验、事件发布由
 * {@link cn.realm.cloud.system.server.user.internal.application.UserService} 负责。
 *
 * <p><b>不映射密码：</b>{@code UserCreateRequest.password} 只在
 * Controller → Service 之间传递，由 Service 层用 {@code PasswordEncoder}
 * 加密后设置 {@code UserDO.passwordHash}。本类的
 * {@link #fromCreateRequest(UserCreateRequest)} 不读取 {@code request.password()}，
 * {@link #toResponse(UserDO)} 也不回显任何密码相关字段——密码的加密和
 * 不暴露是 Service 层和 Repository 层的双重保证。
 *
 * @author QI Guang
 */
public final class UserAdminConverter {

    private UserAdminConverter() {
    }

    /**
     * 创建请求 → 新 {@link UserDO}。
     *
     * <p>只映射请求中允许客户端提供的“非密码”字段。
     * {@code passwordHash}、租户 ID、审计字段由 Service 层在持久化前补齐：
     * <ul>
     *   <li>{@code passwordHash}——由 {@code UserServiceImpl.createUser}
     *       用 {@code PasswordEncoder} 加密明文密码后设置</li>
     *   <li>{@code tenantId}——由租户拦截器自动填充</li>
     *   <li>{@code createBy} / {@code createTime}——由 {@code MetaObjectHandler}
     *       自动填充（如果项目配置了），或由 Service 层显式设置</li>
     * </ul>
     */
    public static UserDO fromCreateRequest(UserCreateRequest request) {
        if (request == null) {
            return null;
        }
        UserDO user = new UserDO();
        user.setUsername(request.username());
        user.setNickname(request.nickname());
        user.setDepartmentId(request.departmentId());
        user.setPhoneNumber(request.phoneNumber());
        user.setEmail(request.email());
        user.setStatus(request.status());
        return user;
    }

    /**
     * 更新请求 → 应用到已有 {@link UserDO}。
     *
     * <p>不包含 {@code username}、{@code status}、{@code password}：
     * <ul>
     *   <li>{@code username}——创建后不可变，登录标识变更会导致审计追溯困难</li>
     *   <li>{@code status}——走独立的状态变更端点，以触发 {@code UserStatusChangedEvent}</li>
     *   <li>{@code password}——密码修改走独立的 {@code changePassword} 流程，
     *       不复用“更新用户资料”的端点</li>
     * </ul>
     *
     * <p>本方法直接修改传入的 {@code user}，调用方（Controller）负责
     * 先 {@code getUser(id)} 拿到完整对象、再 {@code applyUpdateRequest}
     * 应用更新、最后 {@code updateUser}。这样 {@code UserDO} 中未在本方法
     * 映射的字段（如 {@code createTime}、{@code tenantId}）保持数据库原值。
     */
    public static void applyUpdateRequest(UserUpdateRequest request, UserDO user) {
        user.setNickname(request.nickname());
        user.setDepartmentId(request.departmentId());
        user.setPhoneNumber(request.phoneNumber());
        user.setEmail(request.email());
    }

    /**
     * {@link UserDO} → 详情/列表响应。
     *
     * <p>不映射 {@code passwordHash}——密码哈希绝不能出现在 HTTP 响应中，
     * 即使前端“不使用”它，也会暴露在浏览器的网络面板、日志、链路追踪里。
     * 这是“前端权限控制不是安全边界”的具体落地：密码哈希在物理上
     * 就不应该离开服务端。
     */
    public static UserResponse toResponse(UserDO user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getDepartmentId(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getStatus(),
                user.getCreateTime()
        );
    }

    /**
     * 分页结果转换：{@code PageResult<UserDO>} → {@code PageResult<UserResponse>}。
     *
     * <p>保留原分页元数据（总数、页大小、页码），只替换列表元素类型。
     *
     * <p><b>参数顺序提醒：</b>{@code PageResult.of(list, total, pageSize, pageNo)}
     * 是 {@code pageSize} 在前、{@code pageNo} 在后，与
     * {@code PageParam.of(pageNo, pageSize)} 的顺序相反。两个参数都是 {@code long}
     * 或 {@code int}，传反了编译不会报错，但 {@code pages} 和 {@code hasNext}
     * 会算错——这里的调用顺序必须是 {@code (total, pageSize, pageNo)}。
     */
    public static PageResult<UserResponse> toPageResponse(PageResult<UserDO> page) {
        if (page == null) {
            return PageResult.empty();
        }
        List<UserResponse> list = page.getList().stream()
                .map(UserAdminConverter::toResponse)
                .toList();
        return PageResult.of(list, page.getTotal(),
                page.getPageSize(), page.getPageNo());
    }
}
