package cn.realm.cloud.system.server.user.internal.persistence.mapper;

import cn.realm.cloud.framework.mybatis.core.mapper.BaseMapperX;
import cn.realm.cloud.system.server.user.internal.persistence.UserRoleDO;
import cn.realm.cloud.system.server.user.internal.persistence.UserRoleRepositoryImpl;
import org.apache.ibatis.annotations.Mapper;

/**
 * IAM-用户角色关联表 Mapper 接口。
 *
 * <p>继承 {@link BaseMapperX} 以获得 Lambda 便捷查询、批量操作等扩展能力。
 * 关联表没有逻辑删除列，查询和删除都不需要附加 {@code is_deleted = 0} 条件。
 *
 * <p>本接口仅在本层及其实现类 {@link UserRoleRepositoryImpl} 中使用，
 * 不向上泄漏到 {@code internal.application}。
 *
 * @author QI Guang
 */
@Mapper
public interface UserRoleMapper extends BaseMapperX<UserRoleDO> {
}
