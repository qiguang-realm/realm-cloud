package cn.realm.cloud.system.server.role.internal.persistence.mapper;

import cn.realm.cloud.framework.mybatis.core.mapper.BaseMapperX;
import cn.realm.cloud.system.server.role.internal.persistence.RoleDO;
import cn.realm.cloud.system.server.role.internal.persistence.RoleRepositoryImpl;
import org.apache.ibatis.annotations.Mapper;

/**
 * IAM-角色表 Mapper 接口。
 *
 * <p>继承 {@link BaseMapperX} 以获得 Lambda 便捷查询、统一分页、批量操作等扩展能力。
 *
 * <p>本接口仅在本层及其实现类 {@link RoleRepositoryImpl} 中使用，
 * 不向上泄漏到 {@code internal.application}。业务查询条件由
 * {@code RoleRepositoryImpl} 构造，本接口不承载业务逻辑。
 *
 * @author QI Guang
 */
@Mapper
public interface RoleMapper extends BaseMapperX<RoleDO> {
}
