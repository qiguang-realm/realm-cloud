package cn.realm.cloud.system.server.organization.internal.persistence.mapper;

import cn.realm.cloud.framework.mybatis.core.mapper.BaseMapperX;
import cn.realm.cloud.system.server.organization.internal.persistence.DepartmentDO;
import cn.realm.cloud.system.server.organization.internal.persistence.DepartmentRepositoryImpl;
import org.apache.ibatis.annotations.Mapper;

/**
 * IAM-部门表 Mapper 接口。
 *
 * <p>继承 {@link BaseMapperX} 以获得 Lambda 便捷查询、树形查询辅助、
 * 批量操作等扩展能力；其中的 {@code BaseMapper} 基础 CRUD
 * 与 {@code MPJBaseMapper} 连表能力同时可用。
 *
 * <p>本接口仅在本层及其实现类 {@link DepartmentRepositoryImpl} 中使用，
 * 不向上泄漏到 {@code internal.application}。业务查询条件（逻辑删除、
 * 父节点过滤、祖先链前缀）由 {@code DepartmentRepositoryImpl} 构造，
 * 本接口不承载业务逻辑。
 *
 * @author QI Guang
 */
@Mapper
public interface DepartmentMapper extends BaseMapperX<DepartmentDO> {
}
