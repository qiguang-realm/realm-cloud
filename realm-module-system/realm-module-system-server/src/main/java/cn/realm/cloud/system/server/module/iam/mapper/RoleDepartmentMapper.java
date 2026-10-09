package cn.realm.cloud.system.server.module.iam.mapper;

import cn.realm.cloud.system.server.module.iam.model.entity.RoleDepartment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * IAM-角色与部门关联表 Mapper 接口
 * </p>
 *
 * @author QI Guang
 */
@Mapper
public interface RoleDepartmentMapper extends BaseMapper<RoleDepartment> {

}
